import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Properties

plugins {
    java
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("com.gradleup.shadow") version "8.3.11"
}

val managerRoot = file("../..")
val managerProperties = Properties().apply { managerRoot.resolve("gradle.properties").inputStream().use(::load) }
version = providers.gradleProperty("mod_version").getOrElse(managerProperties.getProperty("mod_version"))
val kotlinEnabled = providers.gradleProperty("enable_kotlin").orNull?.toBoolean()
    ?: managerProperties.getProperty("enable_kotlin").toBoolean()
layout.buildDirectory.set(managerRoot.resolve(if (kotlinEnabled) "build/dlc-bootstrap" else "build/java-only/dlc-bootstrap"))

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.neoforged.net/releases/")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
configurations.compileClasspath { attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21) }
val bootstrapLibraries = configurations.create("bootstrapLibraries")
val launcher = sourceSets.create("launcher")
configurations.named(launcher.compileClasspathConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
    attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
}
val neoForgeModLauncher = configurations.create("neoForgeModLauncher")
val forgeLocatorApi = configurations.create("forgeLocatorApi")
val neoForgeLocatorApi = configurations.create("neoForgeLocatorApi")
neoForgeLocatorApi.attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)

val sharedClasses = listOf(
    "bootstrap/DlcEarlyInstaller", "bootstrap/DlcDistributionPaths", "bootstrap/DlcStartupRequirements",
    "client/download/DlcCurseForgeClient", "client/download/DlcModrinthClient",
    "client/download/DlcDownloadCandidate", "client/download/DlcDownloadHeaderUtil",
    "client/download/DlcDownloadService", "client/download/DlcDownloadUtil", "client/download/DlcSharedService",
    "client/remote/DlcRemoteEntry",
    "client/required/DlcRequiredDependency", "client/required/DlcRequiredEntry", "client/required/DlcRequiredLoader",
    "client/required/DlcRequiredRuntime", "client/required/DlcRequiredRuntimeState", "client/required/DlcRequiredService",
    "core/DlcFileMatch", "core/DlcResourceHistory",
    "core/DlcConfig", "core/DlcDcConfigIO", "core/DlcDcFormatUtil", "core/DlcPaths", "core/DlcType",
    "platform/DlcPlatformServices", "platform/PlatformServices",
)
sourceSets.main {
    if (kotlinEnabled) resources.srcDir("src/kotlinRuntime/resources")
    if (kotlinEnabled) {
        java.setSrcDirs(emptyList<String>())
    } else {
        java.srcDir(managerRoot.resolve("src/main/java"))
        java.include(listOf("DlcBootstrapService", "DlcModPayload").map { "com/cozooo/dlc_manager/bootstrap/$it.java" })
        java.include(sharedClasses.map { "com/cozooo/dlc_manager/$it.java" })
    }
}
if (kotlinEnabled) {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        sourceSets.named("main") {
            kotlin.srcDir(managerRoot.resolve("src/main/kotlin"))
            kotlin.include(listOf("DlcBootstrapService", "DlcModPayload").map { "com/cozooo/dlc_manager/bootstrap/$it.kt" })
            kotlin.include(sharedClasses.map { "com/cozooo/dlc_manager/$it.kt" })
        }
    }
}

sourceSets.test {
    compileClasspath += launcher.output
    runtimeClasspath += launcher.output
    java.srcDir(managerRoot.resolve("src/test/java"))
    java.include("com/cozooo/dlc_manager/bootstrap/**")
}

dependencies {
    if (kotlinEnabled) add(bootstrapLibraries.name, "org.jetbrains.kotlin:kotlin-stdlib:2.2.20") { isTransitive = false }
    implementation("com.cozooo:dclanguage:0.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
    compileOnly("cpw.mods:modlauncher:10.0.9") { isTransitive = false }
    compileOnly("net.minecraftforge:fmlloader:1.20.1-47.2.0") { isTransitive = false }
    compileOnly("net.minecraftforge:forgespi:7.0.1") { isTransitive = false }
    compileOnly("net.neoforged.fancymodloader:loader:4.0.42") { isTransitive = false }
    testImplementation("cpw.mods:modlauncher:10.0.9") { isTransitive = false }
    testImplementation("org.junit.jupiter:junit-jupiter:${managerProperties.getProperty("junit_jupiter_version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("net.minecraftforge:fmlloader:1.20.1-47.2.0")
    add(forgeLocatorApi.name, "net.minecraftforge:fmlloader:1.20.1-47.2.0")
    add(neoForgeLocatorApi.name, "net.neoforged.fancymodloader:loader:4.0.42")
    add(neoForgeModLauncher.name, "cpw.mods:modlauncher:11.0.5") { isTransitive = false }
}

val bootstrapJars = listOf("forge" to "1.20.1", "neoforge" to "1.21.1").map { (loader, minecraft) ->
    val profileDir = layout.buildDirectory.dir("profiles/$loader")
    val profile = tasks.register("${loader}Profile") {
        outputs.dir(profileDir)
        inputs.property("loader", loader)
        inputs.property("minecraft", minecraft)
        inputs.property("kotlinEnabled", kotlinEnabled)
        inputs.files(bootstrapLibraries)
        doLast {
            val directory = profileDir.get().asFile.apply { deleteRecursively(); mkdirs() }
            val service = if (loader == "forge") "net.minecraftforge.forgespi.locating.IModLocator" else "net.neoforged.neoforgespi.locating.IModFileCandidateLocator"
            val provider = if (loader == "forge") "DlcForgePayloadBridge" else "DlcNeoForgePayloadBridge"
            directory.resolve("META-INF/services/$service").apply { parentFile.mkdirs() }.writeText("com.cozooo.dlc_bootstrap.launch.$provider\n")
            directory.resolve("META-INF/services/cpw.mods.modlauncher.api.ITransformationService")
                .writeText("com.cozooo.dlc_bootstrap.launch.DlcBootstrapBridge\n")
            val libraries = bootstrapLibraries.files.sortedBy { it.name }
            directory.resolve("dlc-bootstrap.properties").writeText(
                "loader=$loader\nminecraft=$minecraft\nruntimeLibraries=${libraries.joinToString(",") { "META-INF/jarjar/${it.name}" }}\n"
            )
            if (kotlinEnabled) {
                directory.resolve("META-INF/jarjar/metadata.json").apply { parentFile.mkdirs() }.writeText(
                    """{"jars":[{"identifier":{"group":"org.jetbrains.kotlin","artifact":"kotlin-stdlib"},"version":{"range":"[2.2.20,2.3)","artifactVersion":"2.2.20"},"path":"META-INF/jarjar/kotlin-stdlib-2.2.20.jar"}]}"""
                )
            }
            directory.resolve("META-INF/NOTICE.txt").writeText(
                "DLC Manager early installation service: bundled third-party components.\n" +
                    "Gson 2.10.1 (Apache-2.0): META-INF/dlc-manager/licenses/GSON-COPYRIGHT.txt\n" +
                    (if (kotlinEnabled) "Kotlin JVM Standard Library 2.2.20: META-INF/dlc-manager/licenses/kotlin/ATTRIBUTION.txt\n" else "") +
                    "License files apply to the identified third-party components; DLC Manager retains its own license.\n"
            )
        }
    }
    val runtimeJar = tasks.register<ShadowJar>("${loader}RuntimeJar") {
        dependsOn(tasks.classes, profile)
        from(sourceSets.main.get().output)
        from(profileDir)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains:annotations:.*"))
        }
        archiveFileName.set("DLC-Manager-runtime-$loader-$minecraft-${project.version}.jar")
        destinationDirectory.set(layout.buildDirectory.dir("services/$loader"))
        relocate("com.cozooo.dlc_manager", "com.cozooo.dlc_bootstrap.internal")
        relocate("com.cozooo.dclanguage", "com.cozooo.dlc_bootstrap.internal.dclanguage")
        relocate("com.google.gson", "com.cozooo.dlc_bootstrap.internal.gson")
        exclude("META-INF/services/**", "META-INF/jarjar/**", "META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "org/jetbrains/annotations/**")
    }
    tasks.register<Jar>("${loader}BootstrapJar") {
        dependsOn(launcher.classesTaskName, runtimeJar, profile)
        from(launcher.output)
        from(profileDir)
        from("src/main/resources") { include("META-INF/dlc-manager/licenses/**") }
        if (kotlinEnabled) from("src/kotlinRuntime/resources")
        from(runtimeJar.flatMap { it.archiveFile }) {
            into("META-INF/dlc-manager")
            rename { "early.jar" }
        }
        from(bootstrapLibraries) { into("META-INF/jarjar") }
        exclude("**/${if (loader == "forge") "DlcNeoForgePayloadBridge" else "DlcForgePayloadBridge"}*.class")
        archiveFileName.set("DLC-Manager-bootstrap-$loader-$minecraft-${project.version}.jar")
        destinationDirectory.set(layout.buildDirectory.dir("services/$loader"))
        manifest.attributes("FMLModType" to "LIBRARY", "Automatic-Module-Name" to "dlc.manager.bootstrap.$loader")
    }
}
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    from("../../LICENSE") { into("META-INF/dlc-manager") }
    from("../../LICENSING.md") { into("META-INF/dlc-manager") }
    from("../../LICENSING.zh.md") { into("META-INF/dlc-manager") }
}

tasks.shadowJar { enabled = false }
tasks.assemble { dependsOn(bootstrapJars) }
tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    inputs.files(neoForgeModLauncher, forgeLocatorApi, neoForgeLocatorApi, bootstrapLibraries)
    doFirst {
        systemProperty("kotlinStdlibJar", bootstrapLibraries.files.singleOrNull()?.absolutePath ?: "")
        systemProperty("neoForgeModLauncher", neoForgeModLauncher.singleFile.absolutePath)
        systemProperty("forgeLocatorApi", forgeLocatorApi.asPath)
        systemProperty("neoforgeLocatorApi", neoForgeLocatorApi.asPath)
    }
    dependsOn(bootstrapJars)
    bootstrapJars.forEach { jar ->
        inputs.file(jar.flatMap { it.archiveFile })
        systemProperty(jar.name, jar.get().archiveFile.get().asFile.absolutePath)
    }
}
