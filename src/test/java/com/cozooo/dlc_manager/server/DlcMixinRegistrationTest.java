package com.cozooo.dlc_manager.server;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class DlcMixinRegistrationTest {
    private static final String CONFIG = "dlc_manager.mixins.json";
    @TempDir Path temporary;
    private JarFile artifact() throws Exception {
        JarFile jar = new JarFile(System.getProperty("dlc_manager.test.jar"));
        var payload = jar.getJarEntry("META-INF/dlc-manager/mod.jar");
        if (payload == null) return jar;
        Path extracted = Files.createTempFile(temporary, "mod-", ".jar");
        try (jar; InputStream input = jar.getInputStream(payload)) { Files.copy(input, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        return new JarFile(extracted.toFile());
    }
    private String read(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry(path);
        assertNotNull(entry, "Missing archive entry: " + path);
        try (InputStream input = jar.getInputStream(entry)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }

    @Test void packagedMixinIsRegisteredUsingTheLoadersSupportedMetadata() throws Exception {
        try (JarFile jar = artifact()) {
            String loader = System.getProperty("dlc_manager.test.loader");
            if ("forge".equals(loader)) {
                String registered = jar.getManifest().getMainAttributes().getValue("MixinConfigs");
                assertNotNull(registered, "Forge requires MixinConfigs in the jar manifest");
                assertTrue(Arrays.stream(registered.split(",")).map(String::trim).anyMatch(CONFIG::equals));
            } else if ("neoforge".equals(loader)) {
                String toml = read(jar, "META-INF/neoforge.mods.toml");
                assertTrue(toml.contains("[[mixins]]\nconfig=\"" + CONFIG + "\""));
            } else {
                var json = JsonParser.parseString(read(jar, "fabric.mod.json")).getAsJsonObject();
                boolean registered = false;
                for (var value : json.getAsJsonArray("mixins")) registered |= CONFIG.equals(value.getAsString());
                assertTrue(registered);
            }
            var config = JsonParser.parseString(read(jar, CONFIG)).getAsJsonObject();
            for (var name : config.getAsJsonArray("mixins")) {
                String path = (config.get("package").getAsString() + "." + name.getAsString()).replace('.', '/') + ".class";
                assertNotNull(jar.getJarEntry(path), "Registered Mixin class must be packaged");
            }
        }
    }

    @Test void forgeDevelopmentLaunchAlsoRegistersTheMixin() throws Exception {
        if (!"forge".equals(System.getProperty("dlc_manager.test.loader"))) return;
        List<String> arguments = Files.readAllLines(Path.of(System.getProperty("dlc_manager.test.launchConfig"))).stream().map(String::trim).toList();
        int argument = arguments.indexOf("--mixin.config");
        assertTrue(argument >= 0 && argument + 1 < arguments.size(), "Forge development launch is missing --mixin.config");
        assertEquals(CONFIG, arguments.get(argument + 1));
    }

    @Test void injectionSelectsAnInitializerMethodThatExistsInThisMinecraftVersion() throws Exception {
        Set<String> targets = new LinkedHashSet<>(), selectors = new LinkedHashSet<>();
        try (JarFile jar = artifact(); InputStream input = jar.getInputStream(jar.getJarEntry("com/cozooo/dlc_manager/mixin/DlcServerConnectionListenerMixin.class"))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    return "Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor) ? strings("targets", targets) : null;
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            return "Lorg/spongepowered/asm/mixin/injection/Inject;".equals(descriptor) ? strings("method", selectors) : null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertFalse(targets.isEmpty()); assertFalse(selectors.isEmpty());
        for (String target : targets) {
            Set<String> methods = new HashSet<>();
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(target.replace('.', '/') + ".class")) {
                assertNotNull(input, "Mixin target does not exist: " + target);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        if (descriptor.equals("(Lio/netty/channel/Channel;)V")) { methods.add(name); methods.add(name + descriptor); }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            for (String selector : selectors) assertTrue(methods.contains(selector), "Mixin selector does not match the Netty initializer: " + target + "." + selector);
        }
    }

    private AnnotationVisitor strings(String field, Set<String> values) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitArray(String name) {
                return field.equals(name) ? new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public void visit(String name, Object value) { values.add((String)value); }
                } : null;
            }
        };
    }
}
