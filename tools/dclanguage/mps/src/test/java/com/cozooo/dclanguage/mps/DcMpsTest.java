package com.cozooo.dclanguage.mps;

import static org.junit.jupiter.api.Assertions.*;
import static com.cozooo.dclanguage.mps.DcConcepts.*;
import com.cozooo.dclanguage.*;
import com.cozooo.dclanguage.mps.persistence.*;
import com.google.gson.JsonParser;
import jetbrains.mps.tool.environment.*;
import jetbrains.mps.smodel.language.LanguageRegistry;
import jetbrains.mps.openapi.editor.descriptor.EditorAspectDescriptor;
import jetbrains.mps.text.rt.TextGenAspectDescriptor;
import jetbrains.mps.text.impl.RegularTextUnit;
import jetbrains.mps.text.TextUnit;
import jetbrains.mps.extapi.persistence.FileDataSource;
import jetbrains.mps.vfs.VFSManager;
import org.jetbrains.mps.openapi.model.*;
import org.jetbrains.mps.openapi.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DcMpsTest {
    private MpsEnvironment environment;
    private jetbrains.mps.project.Project project;
    @TempDir Path directory;
    @BeforeAll void startMps() {
        environment = new MpsEnvironment(EnvironmentConfig.emptyConfig().withCorePlugin().withRepositoryPlugin().withBootstrapLibraries().withWorkbenchPath());
        environment.init();
        environment.getPlatform().findComponent(jetbrains.mps.extapi.persistence.ModelFactoryService.class).register(new DcModelFactory());
        project = environment.openProject(new java.io.File(System.getProperty("dc.mps.project")));
    }
    @AfterAll void stopMps() { if (environment != null) { if (project != null) environment.closeProject(project); environment.dispose(); } }
    @Test void languageAndEditorLoadFromRealModule() {
        project.getModelAccess().runReadAction(() -> {
            var runtime = environment.getPlatform().findComponent(LanguageRegistry.class).getLanguage(LANGUAGE);
            assertNotNull(runtime, "MPS must load the compiled DC language from its .mpl descriptor");
            assertEquals(1, runtime.getAspect(jetbrains.mps.lang.typesystem.runtime.IHelginsDescriptor.class).getNonTypesystemRules().size());
            assertTrue(DOCUMENT.isValid());
            assertTrue(DOCUMENT.isRootable());
            assertNotNull(DOCUMENT.getSourceNode().resolve(project.getRepository()), "source=" + DOCUMENT.getSourceNode());
            assertTrue(VALUE.isAbstract());
            assertTrue(OBJECT.isSubConceptOf(VALUE));
            assertEquals(1, runtime.getAspect(EditorAspectDescriptor.class).getDeclaredEditors(DOCUMENT).size());
            assertNotNull(runtime.getAspect(TextGenAspectDescriptor.class).getDescriptor(DOCUMENT));
        });
    }
    @Test void structureModelContainsRealConcepts() {
        project.getModelAccess().runReadAction(() -> {
            var models = new ArrayList<SModel>();
            for (var module : project.getModules()) module.getModels().forEach(models::add);
            var structure = models.stream().filter(m -> m.getName().getValue().equals(NAMESPACE + ".structure")).findFirst().orElseThrow();
            List<String> names = new ArrayList<>();
            for (var node : structure.getRootNodes()) names.add(node.getProperty(NAME));
            assertEquals(List.of("Document", "Member", "Value", "ObjectValue", "ArrayValue", "ScalarValue"), names);
        });
    }
    @Test void existingLanguageFixturesRoundTripLosslessly() throws Exception {
        Path fixtures = Path.of(System.getProperty("dc.mps.project"), "../src/test/resources/compatibility");
        try (var files = Files.list(fixtures)) {
            for (Path fixture : files.filter(p -> p.toString().endsWith(".dc")).toList()) {
                String source = Files.readString(fixture);
                assertEquals(source, DcModelCodec.write(DcModelCodec.read(fixture.getFileName().toString(), source)), fixture.toString());
            }
        }
    }
    @Test void nestedObjectsArraysAllScalarTypesAndUnknownFieldsSurvive() throws Exception {
        String source = "# title\r\n[custom.\"with.dots\"]\r\na = [true, null, 1.2300e20, {\"a\": [\"中😀\", false]}]\r\n";
        var root = DcModelCodec.read("test", source);
        assertEquals(source, DcModelCodec.write(root));
        assertEquals(DcDocuments.readText(source).toJson(), DcModelCodec.document(root).toJson());
    }
    @Test void editedPropertyKeepsCommentsAndOtherFormatting() throws Exception {
        String source = "# retained\r\n[basic] # category\r\nidentifier   = 'old' # retained inline\r\nunknown=42\r\n";
        var root = DcModelCodec.read("test", source);
        scalar(root, "basic", "identifier").setProperty(TEXT, "\"new\"");
        assertEquals(source.replace("'old'", "'new'"), DcModelCodec.write(root));
    }
    @Test void quotedWhitespaceNamesAndBomArePreserved() throws Exception {
        String source = "\uFEFF# utf8\r\n[\" \"]\r\n\" \" = \"value\"\r\n";
        assertEquals(source, DcModelCodec.write(DcModelCodec.read("quoted", source)));
    }
    @Test void invalidUtf8IsRejectedWithoutReplacingBytes() throws Exception {
        Path file = directory.resolve("invalid-utf8.dc");byte[] original = {120,61,(byte)0xc3,40};Files.write(file,original);
        var source = source(file);var factory = new DcModelFactory();var model = factory.load(source);
        assertThrows(ModelSaveException.class, () -> factory.save(model, source));
        assertArrayEquals(original, Files.readAllBytes(file));
    }
    @Test void nodesHaveStableIdsAcrossReloads() throws Exception {
        var first = DcModelCodec.read("a", "[basic]\nidentifier=\"x\"\n");
        var second = DcModelCodec.read("b", "# moved\n[basic]\nidentifier=\"x\"\n");
        assertEquals(scalar(first, "basic", "identifier").getNodeId(), scalar(second, "basic", "identifier").getNodeId());
    }
    @Test void duplicateFieldsAndInvalidValuesAreRejected() throws Exception {
        var root = DcModelCodec.read("a", "x=1\ny=2\n");
        var iterator = root.getChildren(ROOT_MEMBERS).iterator();
        iterator.next(); iterator.next().setProperty(NAME, "x");
        assertFalse(DcModelCodec.validate(root).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> DcModelCodec.write(root));
        var valid = DcModelCodec.read("a", "x=1\n");
        valid.getChildren(ROOT_MEMBERS).iterator().next().getChildren(MEMBER_VALUE).iterator().next().setProperty(TEXT, "1\ninjected=2");
        assertThrows(IllegalArgumentException.class, () -> DcModelCodec.write(valid));
    }
    @Test void factorySavesRealDcFilesAtomically() throws Exception {
        Path file = directory.resolve("test.dc"); Files.writeString(file, "# keep\n[basic]\nx=1\n");
        var factory = new DcModelFactory(); var source = source(file);
        var model = factory.load(source);
        scalar(DcModelDescriptor.root(model), "basic", "x").setProperty(TEXT, "2");
        assertTrue(((EditableSModel) model).isChanged(), "Edits must be marked dirty so MPS saves them");
        factory.save(model, source);
        assertEquals("# keep\n[basic]\nx=2\n", Files.readString(file));
        assertFalse(((EditableSModel) model).isChanged());
        try (var files = Files.list(directory)) { assertEquals(1L, files.count()); }
    }
    @Test void invalidModelNeverOverwritesOriginal() throws Exception {
        Path file = directory.resolve("broken.dc"); String source = "x=\"unterminated\n";Files.writeString(file, source);
        var factory = new DcModelFactory();var dataSource = source(file);var model = factory.load(dataSource);
        assertThrows(ModelSaveException.class, () -> factory.save(model, dataSource));
        assertEquals(source, Files.readString(file));
    }
    @Test void externalEditIsDetectedBeforeSaving() throws Exception {
        Path file = directory.resolve("conflict.dc"); Files.writeString(file, "x=1\n");
        var factory = new DcModelFactory();var source = source(file);var model = factory.load(source);
        model.load();Files.writeString(file, "x=2\n");
        assertThrows(java.io.IOException.class, () -> factory.save(model, source));
        assertEquals("x=2\n", Files.readString(file));
    }
    @Test void reloadReadsExternalChanges() throws Exception {
        Path file = directory.resolve("reload.dc");Files.writeString(file, "x=1\n");
        var model = (DcModelDescriptor) new DcModelFactory().load(source(file));model.load();
        Files.writeString(file, "x=3\n");model.setChanged(false);model.reloadFromSource();
        assertEquals("x=3\n", DcModelCodec.write(DcModelDescriptor.root(model)));
    }
    @Test void factoryCreatesAndSavesNewDocument() throws Exception {
        Path file = directory.resolve("created.dc");var source = source(file);var factory = new DcModelFactory();
        var model = factory.create(source, new SModelName("created"));factory.save(model,source);
        assertTrue(Files.exists(file));assertEquals(0, DcDocuments.read(file).root().size());
        assertThrows(ModelCreationException.class, () -> factory.create(source, new SModelName("created")));
    }
    @Test void textGenerationUsesTheSameWriter() throws Exception {
        var root = DcModelCodec.read("generated", "# comment\n[basic]\nx=\"中\"\n");
        project.getModelAccess().runReadAction(() -> {
            RegularTextUnit unit = new RegularTextUnit(root, "generated.dc", null, java.nio.charset.StandardCharsets.UTF_8, environment.getPlatform());unit.generate();
            assertEquals(TextUnit.Status.Generated, unit.getState());
            assertEquals(DcModelCodec.write(root), new String(unit.getBytes(), java.nio.charset.StandardCharsets.UTF_8));
        });
    }
    @Test void invalidEditedValueDoesNotTruncateFileAndCanBeFixed() throws Exception {
        Path file = directory.resolve("invalid-edit.dc"); String original = "[basic]\nx=1\n"; Files.writeString(file, original);
        var factory = new DcModelFactory(); var source = source(file); var model = factory.load(source);
        var scalar = scalar(DcModelDescriptor.root(model), "basic", "x"); scalar.setProperty(TEXT, "\"unterminated");
        assertThrows(ModelSaveException.class, () -> factory.save(model, source));
        assertEquals(original, Files.readString(file));
        assertThrows(IllegalStateException.class, ((EditableSModel) model)::save);
        scalar.setProperty(TEXT, "2"); ((EditableSModel) model).save();
        assertEquals("[basic]\nx=2\n", Files.readString(file));
    }
    @Test void deletedMemberAndAddedNestedObjectPersist() throws Exception {
        var root = DcModelCodec.read("edit", "# keep\nold=1\n");
        root.removeChild(root.getChildren(ROOT_MEMBERS).iterator().next());
        var added = DcModelCodec.read("other", "[new]\narray=[1,2]\n").getChildren(ROOT_MEMBERS).iterator().next();
        added.getParent().removeChild(added);root.addChild(ROOT_MEMBERS,added);
        assertEquals(JsonParser.parseString("{\"new\":{\"array\":[1,2]}}"), DcModelCodec.document(root).toJson());
        assertEquals(DcModelCodec.document(root).toJson(), DcDocuments.readText(DcModelCodec.write(root)).toJson());
    }
    @Test void registeredFactoryDiscoversSandboxDcModel() {
        project.getModelAccess().runReadAction(() -> {
            boolean found = false;
            for (var module : project.getModules()) for (var model : module.getModels()) {
                if (model.getName().getSimpleName().equals("example")) { found = true; assertEquals(DOCUMENT, DcModelDescriptor.root(model).getConcept()); }
            }
            assertTrue(found, "The default MPS model root must discover .dc files via ModelFactoryService");
        });
    }
    private FileDataSource source(Path file) {
        return new FileDataSource(environment.getPlatform().findComponent(VFSManager.class).getUmbrellaFileSystemJavaIO().getFile(file.toString()));
    }
    private SNode scalar(SNode root, String category, String key) {
        for (var member : root.getChildren(ROOT_MEMBERS)) if (category.equals(member.getProperty(NAME))) {
            var object = member.getChildren(MEMBER_VALUE).iterator().next();
            for (var field : object.getChildren(OBJECT_MEMBERS)) if (key.equals(field.getProperty(NAME))) return field.getChildren(MEMBER_VALUE).iterator().next();
        }
        throw new AssertionError("Missing field: " + category + "." + key);
    }
}
