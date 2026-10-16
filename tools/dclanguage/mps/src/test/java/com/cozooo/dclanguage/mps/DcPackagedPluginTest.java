package com.cozooo.dclanguage.mps;

import static org.junit.jupiter.api.Assertions.*;
import jetbrains.mps.tool.environment.*;
import jetbrains.mps.smodel.language.LanguageRegistry;
import jetbrains.mps.openapi.editor.descriptor.EditorAspectDescriptor;
import jetbrains.mps.extapi.persistence.ModelFactoryService;
import org.jetbrains.mps.openapi.persistence.ModelFactory;
import org.jetbrains.mps.openapi.persistence.datasource.FileExtensionDataSourceType;
import org.junit.jupiter.api.Test;
import java.nio.file.*;

class DcPackagedPluginTest {
    @Test void installedPluginContributesLanguageAndPersistence() throws Exception {
        Path plugin = Path.of(System.getProperty("dc.mps.plugin"));
        String descriptor = Files.readString(plugin.resolve("languages/dc/com.cozooo.dclanguage.mps.mpl"));
        assertTrue(descriptor.contains("classes=\"provided\""));
        assertFalse(descriptor.contains("classes_gen"));
        assertFalse(descriptor.contains("${module}/lib/"));
        assertFalse(descriptor.contains("<classes "));
        assertFalse(descriptor.contains("<library "));
        var configuration = EnvironmentConfig.emptyConfig().withCorePlugin().withRepositoryPlugin().withBootstrapLibraries().withWorkbenchPath()
            .addPlugin(plugin.toString(), "com.cozooo.dclanguage.mps");
        var environment = new MpsEnvironment(configuration);
        try {
            environment.init();
            var runtime = environment.getPlatform().findComponent(LanguageRegistry.class).getLanguage(jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory.getLanguage(0x872f19e2dc714dbaL, 0xa42a0b0d8813df59L, "com.cozooo.dclanguage.mps"));
            assertNotNull(runtime, "The distributed plugin must contribute the DC language without source-project classes");
            assertTrue(runtime.getClass().getProtectionDomain().getCodeSource().getLocation().toString().contains("staged-plugin"));
            assertNotNull(runtime.getAspect(EditorAspectDescriptor.class));
            var definitionLanguage=jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory.getLanguage(0x4b259ac30fd5481eL,0xb3705e307e623184L,"com.cozooo.dclanguage.definition");
            var definitionRuntime=environment.getPlatform().findComponent(LanguageRegistry.class).getLanguage(definitionLanguage);
            assertNotNull(definitionRuntime,"The plugin must include the language-definition DSL");
            assertNotNull(definitionRuntime.getAspect(EditorAspectDescriptor.class));
            assertNotNull(definitionRuntime.getAspect(jetbrains.mps.text.rt.TextGenAspectDescriptor.class));
            var authoring=environment.openProject(Path.of(System.getProperty("dc.mps.project"),"definition-editor").toFile());
            try {
                authoring.getModelAccess().runReadAction(()->{
                    boolean found=false;
                    for(var module:authoring.getModules())for(var model:module.getModels())for(var root:model.getRootNodes())
                        if(root.getConcept().getQualifiedName().equals("com.cozooo.dclanguage.definition.structure.Definition")){
                            found=true;assertTrue(root.getConcept().isValid());
                            assertFalse(definitionRuntime.getAspect(EditorAspectDescriptor.class).getDeclaredEditors(root.getConcept()).isEmpty());
                        }
                    assertTrue(found,"The installed definition editor must open the authoritative native model");
                });
            }finally{environment.closeProject(authoring);}
            ClassLoader loader = runtime.getClass().getClassLoader();
            verifyPersistenceExtension(runtime.getClass(), loader, environment);
            assertTrue(loader.loadClass("com.cozooo.dclanguage.DcDocuments").getProtectionDomain().getCodeSource().getLocation().toString().contains("staged-plugin"));
            var codec = loader.loadClass("com.cozooo.dclanguage.mps.DcModelCodec");
            String text = "# plugin test\n[basic]\nidentifier='example'\n";
            Object root = codec.getMethod("read", String.class, String.class).invoke(null,"example",text);
            assertEquals(text, codec.getMethod("write", org.jetbrains.mps.openapi.model.SNode.class).invoke(null,root));
        } finally { environment.dispose(); }
    }
    private void verifyPersistenceExtension(Class<?> runtime, ClassLoader loader, MpsEnvironment environment) throws Exception {
        var disposable = com.intellij.openapi.util.Disposer.newDisposable();
        jetbrains.mps.components.ComponentPlugin registrar = null;
        try {
            var application = new com.intellij.mock.MockApplication(disposable) {
                @SuppressWarnings("unchecked")
                @Override public <T> Class<T> loadClass(String name, com.intellij.openapi.extensions.PluginDescriptor descriptor) throws ClassNotFoundException {
                    if (descriptor == null || descriptor.getPluginClassLoader() == null) return super.loadClass(name, descriptor);
                    return (Class<T>) descriptor.getPluginClassLoader().loadClass(name);
                }
            };
            com.intellij.openapi.application.ApplicationManager.setApplication(application, disposable);
            com.intellij.openapi.extensions.Extensions.setRootArea(application.getExtensionArea(), disposable);
            var area = com.intellij.openapi.extensions.Extensions.getRootArea();
            var providerPoint = jetbrains.mps.persistence.ModelFactoryRegister.ModelFactoryProvider.EP_MODEL_FACTORY;
            area.registerExtensionPoint(providerPoint.getName(), jetbrains.mps.persistence.ModelFactoryRegister.ModelFactoryProvider.class.getName(), com.intellij.openapi.extensions.ExtensionPoint.Kind.BEAN_CLASS);
            area.registerExtensionPoint(jetbrains.mps.ide.persistence.ModelIdFactoryExtension.EXTENSION_POINT.getName(), jetbrains.mps.ide.persistence.ModelIdFactoryExtension.class.getName(), com.intellij.openapi.extensions.ExtensionPoint.Kind.BEAN_CLASS);
            area.registerExtensionPoint(jetbrains.mps.ide.persistence.ModelRootFactoryEP.EP_NAME.getName(), jetbrains.mps.ide.persistence.ModelRootFactoryEP.class.getName(), com.intellij.openapi.extensions.ExtensionPoint.Kind.BEAN_CLASS);
            area.registerExtensionPoint(jetbrains.mps.persistence.DataSourceFactoryRuleProvider.EP_DATA_SOURCE_FACTORY.getName(), jetbrains.mps.persistence.DataSourceFactoryRuleProvider.class.getName(), com.intellij.openapi.extensions.ExtensionPoint.Kind.BEAN_CLASS);
            var pluginDescriptor = new com.intellij.openapi.extensions.DefaultPluginDescriptor(com.intellij.openapi.extensions.PluginId.getId("com.cozooo.dclanguage.mps"), loader);
            try (var jar = new java.util.jar.JarFile(Path.of(runtime.getProtectionDomain().getCodeSource().getLocation().toURI()).toFile());
                 var input = jar.getInputStream(jar.getJarEntry("META-INF/plugin.xml"))) {
                var xml = jetbrains.mps.util.JDOMUtil.loadDocument(input).getRootElement();
                var extensions = xml.getChild("extensions");
                assertEquals("com.intellij", extensions.getAttributeValue("defaultExtensionNs"));
                var declaration = extensions.getChild("mps.ModelFactoryProvider");
                assertNotNull(declaration, "The plugin archive must declare the actual MPS persistence extension");
                var bean = com.intellij.util.xmlb.XmlSerializer.deserialize(declaration, jetbrains.mps.persistence.ModelFactoryRegister.ModelFactoryProvider.class);
                bean.setPluginDescriptor(pluginDescriptor);
                area.getExtensionPoint(providerPoint).registerExtension(bean, pluginDescriptor, disposable);
            }
            registrar = new jetbrains.mps.persistence.ModelFactoryRegister.Factory().create(environment.getPlatform());
            registrar.init();
            var factory = environment.getPlatform().findComponent(ModelFactoryService.class).getDefaultModelFactory(FileExtensionDataSourceType.of("dc"));
            assertNotNull(factory, "Official ModelFactoryRegister must instantiate the extension from packaged plugin.xml");
            assertEquals("com.cozooo.dclanguage.mps.persistence.DcModelFactory", factory.getClass().getName());
            assertSame(loader, factory.getClass().getClassLoader());
            var project = environment.openProject(Path.of(System.getProperty("dc.mps.project"), "example").toFile());
            try {
                project.getModelAccess().runReadAction(() -> {
                    boolean found = false;
                    for (var module : project.getModules()) for (var model : module.getModels()) {
                        if (model.getName().getSimpleName().equals("example")) {
                            found = true;
                            assertEquals("com.cozooo.dclanguage.mps.structure.Document", model.getRootNodes().iterator().next().getConcept().getQualifiedName());
                            assertEquals(loader, model.getClass().getClassLoader());
                        }
                    }
                    assertTrue(found, "An installed plugin must discover example.dc in its documented example project");
                });
            } finally { environment.closeProject(project); }
            Path file = Files.createTempFile("dc-mps-extension-", ".dc");
            try {
                String text = "# extension round trip\n[basic]\nidentifier='example'\n";
                Files.writeString(file, text);
                var source = new jetbrains.mps.extapi.persistence.FileDataSource(environment.getPlatform().findComponent(jetbrains.mps.vfs.VFSManager.class).getUmbrellaFileSystemJavaIO().getFile(file.toString()));
                var model = factory.load(source);
                assertTrue(model.getRootNodes().iterator().hasNext());
                factory.save(model, source);
                assertEquals(text, Files.readString(file));
            } finally { Files.deleteIfExists(file); }
        } finally {
            if (registrar != null) registrar.dispose();
            com.intellij.openapi.util.Disposer.dispose(disposable);
        }
    }
}
