package com.cozooo.dclanguage.mps;

import com.cozooo.dclanguage.definition.DefinitionCompiler;
import com.cozooo.dclanguage.definition.DefinitionConcepts;
import com.google.gson.JsonElement;
import jetbrains.mps.tool.environment.EnvironmentConfig;
import jetbrains.mps.tool.environment.MpsEnvironment;
import org.jetbrains.mps.openapi.model.EditableSModel;
import org.jetbrains.mps.openapi.model.SNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedRuntimeTest {
    @TempDir Path temporary;

    @Test void editingTheMpsDefinitionChangesCompiledRuntimeAndLanguageService() throws Exception {
        Path projectPath = Path.of(System.getProperty("dc.mps.project"));
        var environment = new MpsEnvironment(EnvironmentConfig.emptyConfig().withCorePlugin().withRepositoryPlugin().withBootstrapLibraries().withWorkbenchPath());
        environment.init();
        var project = environment.openProject(projectPath.toFile());
        AtomicReference<Map<String, String>> generated = new AtomicReference<>();
        try {
            project.getModelAccess().executeCommand(() -> {
                SNode definition = null;
                for (var module : project.getModules()) for (var model : module.getModels()) for (var root : model.getRootNodes()) {
                    if (root.getConcept().equals(DefinitionConcepts.concept("Definition"))) definition = root;
                }
                assertNotNull(definition);
                var assignment = named(definition, "tokens", "assignment");
                var comment = named(definition, "tokens", "comment");
                var bare = named(definition, "policies", "allowBareValues");
                var dotted = named(definition, "policies", "allowDottedCategories");
                var categoryOpen = named(definition, "tokens", "categoryOpen");
                var categoryClose = named(definition, "tokens", "categoryClose");
                var bool = named(definition, "literals", "true");
                var profile = named(definition, "profiles", "strict-example");
                var category = DefinitionCompiler.children(profile, "categories").getFirst();
                var field = DefinitionCompiler.children(category, "fields").getFirst();
                var nestedCategory = new jetbrains.mps.smodel.SNode(DefinitionConcepts.concept("Category"));
                nestedCategory.setProperty(DefinitionConcepts.NAME, "nested");
                nestedCategory.setProperty(DefinitionConcepts.property("Category", "required"), "false");
                category.addChild(DefinitionConcepts.child("Category", "categories"), nestedCategory);
                String originalAssignment = DefinitionCompiler.get(assignment, "value");
                String originalComment = DefinitionCompiler.get(comment, "value");
                String originalBare = DefinitionCompiler.get(bare, "value");
                String originalDotted = DefinitionCompiler.get(dotted, "value");
                String originalOpen = DefinitionCompiler.get(categoryOpen, "value");
                String originalClose = DefinitionCompiler.get(categoryClose, "value");
                String originalBool = DefinitionCompiler.get(bool, "value");
                String originalName = DefinitionCompiler.get(field, "name");
                try {
                    assignment.setProperty(DefinitionConcepts.property("SyntaxToken", "value"), ":=");
                    comment.setProperty(DefinitionConcepts.property("SyntaxToken", "value"), "%%");
                    bare.setProperty(DefinitionConcepts.property("Policy", "value"), "false");
                    dotted.setProperty(DefinitionConcepts.property("Policy", "value"), "false");
                    categoryOpen.setProperty(DefinitionConcepts.property("SyntaxToken", "value"), "<");
                    categoryClose.setProperty(DefinitionConcepts.property("SyntaxToken", "value"), ">");
                    bool.setProperty(DefinitionConcepts.property("LiteralRule", "value"), "yes");
                    field.setProperty(DefinitionConcepts.NAME, "title");
                    generated.set(DefinitionCompiler.files(definition, "mutation-test"));
                } finally {
                    assignment.setProperty(DefinitionConcepts.property("SyntaxToken", "value"), originalAssignment);
                    comment.setProperty(DefinitionConcepts.property("SyntaxToken", "value"), originalComment);
                    bare.setProperty(DefinitionConcepts.property("Policy", "value"), originalBare);
                    dotted.setProperty(DefinitionConcepts.property("Policy", "value"), originalDotted);
                    categoryOpen.setProperty(DefinitionConcepts.property("SyntaxToken", "value"), originalOpen);
                    categoryClose.setProperty(DefinitionConcepts.property("SyntaxToken", "value"), originalClose);
                    bool.setProperty(DefinitionConcepts.property("LiteralRule", "value"), originalBool);
                    field.setProperty(DefinitionConcepts.NAME, originalName);
                    category.removeChild(nestedCategory);
                    ((EditableSModel) definition.getModel()).setChanged(false);
                }
            });
        } finally { environment.closeProject(project); environment.dispose(); }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        Path classes = Files.createDirectories(temporary.resolve("classes"));
        List<Path> sources = new ArrayList<>();
        try (var files = Files.walk(projectPath.resolve("../src/main/java"))) {
            sources.addAll(files.filter(path -> path.toString().endsWith(".java")).toList());
        }
        for (var file : generated.get().entrySet()) {
            Path output = temporary.resolve(file.getKey());
            Files.createDirectories(output.getParent());
            Files.writeString(output, file.getValue());
            if (file.getKey().startsWith("java/")) sources.add(output);
        }
        Path gson = Path.of(JsonElement.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        StringWriter diagnostics = new StringWriter();
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            assertTrue(compiler.getTask(diagnostics, files, null,
                List.of("--release", "17", "-classpath", gson.toString(), "-d", classes.toString()), null,
                files.getJavaFileObjectsFromPaths(sources)).call(), diagnostics.toString());
        }
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL(), temporary.resolve("resources").toUri().toURL(), gson.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> documents = loader.loadClass("com.cozooo.dclanguage.DcDocuments");
            Class<?> document = loader.loadClass("com.cozooo.dclanguage.DcDocument");
            String source = "%% changed grammar\n<basic>\ntitle := \"Hello\"\nenabled := yes\n";
            Object parsed = documents.getMethod("readText", String.class).invoke(null, source);
            assertEquals("Hello", document.getMethod("getString", String.class, String.class).invoke(parsed, "basic", "title"));
            Object enabled = document.getMethod("get", String.class, String.class).invoke(parsed, "basic", "enabled");
            assertEquals("yes", documents.getMethod("writeValue", loader.loadClass("com.google.gson.JsonElement")).invoke(null, enabled));
            assertTrue((boolean) enabled.getClass().getMethod("getAsBoolean").invoke(enabled));
            document.getMethod("set", String.class, String.class, boolean.class).invoke(parsed, "basic", "another", true);
            String written = (String) documents.getMethod("writeText", document).invoke(null, parsed);
            assertTrue(written.contains("another := yes"), written);
            assertTrue(written.contains("%% changed grammar"));
            InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> documents.getMethod("readText", String.class).invoke(null, "<basic>\ntitle := unquoted\n"));
            assertEquals("DcParseException", error.getCause().getClass().getSimpleName());
            Object nested = documents.getMethod("readText", String.class).invoke(null, "<a>\nx := {}\n<other>\ny := 1\n");
            Class<?> jsonElement = loader.loadClass("com.google.gson.JsonElement");
            Object memberValue = loader.loadClass("com.google.gson.JsonPrimitive").getConstructor(String.class).newInstance("nested");
            document.getMethod("set", List.class, String.class, jsonElement).invoke(nested, List.of("a", "x"), "value", memberValue);
            document.getMethod("asSection", String[].class).invoke(nested, (Object) new String[]{"a", "x"});
            String nestedText = (String) documents.getMethod("writeText", document).invoke(null, nested);
            assertTrue(nestedText.contains("<a>\n\n  <x>"), nestedText);
            Object reread = documents.getMethod("readText", String.class).invoke(null, nestedText);
            Object nestedValue = document.getMethod("get", List.class, String.class).invoke(reread, List.of("a", "x"), "value");
            assertEquals("nested", nestedValue.getClass().getMethod("getAsString").invoke(nestedValue));
            Class<?> serviceClass = loader.loadClass("com.cozooo.dclanguage.DcLanguageService");
            Object service = serviceClass.getConstructor().newInstance();
            Class<?> position = loader.loadClass("com.cozooo.dclanguage.DcLanguageService$Position");
            List<?> items = (List<?>) serviceClass.getMethod("completions", String.class, String.class, position, String.class)
                .invoke(service, "file:///sample.strict.dc", "<basic>\nti", position.getConstructor(int.class, int.class).newInstance(1, 2), "strict-example");
            assertTrue(items.stream().anyMatch(item -> {
                try { return item.getClass().getMethod("insertText").invoke(item).toString().contains("title :="); }
                catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
            }), items.toString());
            List<?> categories = (List<?>) serviceClass.getMethod("completions", String.class, String.class, position, String.class)
                .invoke(service, "file:///sample.strict.dc", "  <", position.getConstructor(int.class, int.class).newInstance(0, 3), "strict-example");
            Object nestedItem = categories.stream().filter(item -> item.toString().contains("nested")).findFirst().orElseThrow();
            String categoryText = nestedItem.getClass().getMethod("insertText").invoke(nestedItem).toString();
            assertEquals("<basic>\n  <nested>", categoryText);
            Object headerDocument = documents.getMethod("readText", String.class).invoke(null, categoryText);
            assertNotNull(document.getMethod("category", String[].class).invoke(headerDocument, (Object) new String[]{"basic", "nested"}));
            List<?> missing = (List<?>) serviceClass.getMethod("diagnostics", String.class, String.class, String.class)
                .invoke(service, "file:///sample.strict.dc", "<basic>\n", "strict-example");
            assertTrue(missing.toString().contains("title"), missing.toString());
            List<?> valid = (List<?>) serviceClass.getMethod("diagnostics", String.class, String.class, String.class)
                .invoke(service, "file:///sample.strict.dc", "<basic>\ntitle := \"Hello\"\n", "strict-example");
            assertTrue(valid.isEmpty(), valid.toString());
        }
    }

    private static SNode named(SNode node, String role, String name) {
        return DefinitionCompiler.children(node, role).stream().filter(child -> DefinitionCompiler.get(child, "name").equals(name)).findFirst().orElseThrow();
    }
}
