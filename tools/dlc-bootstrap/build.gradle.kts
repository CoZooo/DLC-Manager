import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import java.util.Properties

plugins {
    java
    id("org.jetbrains.kotlin.jvm") apply false
    id("com.gradleup.shadow") version "8.3.11"
}

val managerRoot = rootProject.projectDir.resolve("../..").canonicalFile
data class BootstrapTarget(val node: String, val loader: String, val minecraft: String)

val bootstrapTargets = managerRoot.resolve("versions")
    .listFiles { file ->
        file.isDirectory &&
            file.resolve("gradle.properties").isFile &&
            (file.name.endsWith("-forge") || file.name.endsWith("-neoforge"))
    }
    ?.map { directory ->
        val node = directory.name
        BootstrapTarget(
            node = node,
            loader = node.substringAfterLast('-'),
            minecraft = node.substringBeforeLast('-'),
        )
    }
    ?.sortedBy { it.node }
    ?: emptyList()
require(bootstrapTargets.isNotEmpty()) { "The main project does not declare Forge or NeoForge StoneCutter nodes" }

val managerProperties = Properties().apply { managerRoot.resolve("gradle.properties").inputStream().use(::load) }
val kotlinEnabled = providers.gradleProperty("enable_kotlin").orNull?.toBoolean()
    ?: managerProperties.getProperty("enable_kotlin").toBoolean()
val stonecutterBuild = extensions.findByType<StonecutterBuildExtension>()
val currentTarget = stonecutterBuild?.current?.project?.let { node ->
    bootstrapTargets.singleOrNull { it.node == node }
}
    ?: stonecutterBuild?.let { error("Unknown DLC bootstrap StoneCutter node: ${it.current.project}") }
val kotlinStdlibVersion = currentTarget?.let { target ->
    val targetProperties = managerRoot.resolve("versions/${target.node}/gradle.properties")
    if (targetProperties.isFile) Properties().apply { targetProperties.inputStream().use(::load) }
        .getProperty("kotlin_runtime_version")
        ?.takeIf { it.isNotBlank() } else null
} ?: if (currentTarget == null) managerProperties.getProperty("kotlin_version")
    else error("Missing Kotlin runtime version for ${currentTarget.node}")
val kotlinVersionParts = kotlinStdlibVersion.split('.')
require(kotlinVersionParts.size >= 2) { "Invalid main project Kotlin runtime version: $kotlinStdlibVersion" }
val kotlinStdlibUpperBound = "${kotlinVersionParts[0]}.${kotlinVersionParts[1].toInt() + 1}"
val buildRoot = managerRoot.resolve(if (kotlinEnabled) "build/dlc-bootstrap" else "build/java-only/dlc-bootstrap")
val distributionRoot = buildRoot.resolve("services")

version = providers.gradleProperty("mod_version").getOrElse(managerProperties.getProperty("mod_version"))
layout.buildDirectory.set(
    if (currentTarget == null) buildRoot else buildRoot.resolve(currentTarget.node)
)

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.neoforged.net/releases/")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

val sharedClasses = listOf(
    "bootstrap/DlcDistributionPaths",
    "bootstrap/DlcEarlyInstaller",
    "bootstrap/DlcBootstrapI18n",
    "bootstrap/DlcModpackAutoUpdater",
    "bootstrap/DlcStartupRequirements",
    "client/core/DlcZipUtil",
    "client/download/DlcCurseForgeClient",
    "client/download/DlcDownloadCandidate",
    "client/download/DlcDownloadHeaderUtil",
    "client/download/DlcDownloadService",
    "client/download/DlcDownloadUtil",
    "client/download/DlcModrinthClient",
    "client/download/DlcSharedService",
    "client/helper/DlcFileOpsHelperClient",
    "client/pack/DlcPackArchive",
    "client/pack/DlcPackCFResolver",
    "client/pack/DlcPackDcFiles",
    "client/pack/DlcPackInstallPlan",
    "client/pack/DlcPackInstaller",
    "client/pack/DlcPackManifest",
    "client/pack/DlcPackMeta",
    "client/pack/DlcPackMetadata",
    "client/pack/DlcPackPackage",
    "client/pack/DlcPackRecovery",
    "client/pack/DlcPackTransaction",
    "client/pack/DlcPackUpdateCoordinator",
    "client/pack/DlcPackUpdateGuard",
    "client/preset/DlcPresetEntry",
    "client/preset/DlcPresetLoader",
    "client/preset/DlcPresetRuntimeState",
    "client/preset/DlcPresetStateStore",
    "client/preset/DlcPresetStatus",
    "client/remote/DlcRemoteEntry",
    "client/remote/DlcRemoteLoader",
    "client/remote/DlcRemoteMarker",
    "client/required/DlcRequiredDependency",
    "client/required/DlcRequiredEntry",
    "client/required/DlcRequiredLoader",
    "client/required/DlcRequiredRuntime",
    "client/required/DlcRequiredRuntimeState",
    "client/required/DlcRequiredService",
    "client/required/DlcRequiredInstaller",
    "client/selfupdate/DlcSelfUpdateService",
    "client/update/DlcPendingUpdate",
    "client/update/DlcPackReleaseUpdateService",
    "client/update/DlcUpdateCompatibility",
    "client/update/DlcUpdateDcParser",
    "client/update/DlcUpdateHashIndex",
    "client/update/DlcUpdateJournal",
    "client/update/DlcUpdateManifest",
    "client/update/DlcUpdateMeta",
    "client/update/DlcUpdateWorldBackup",
    "client/update/DlcUpdatePackage",
    "client/update/DlcUpdateService",
    "client/update/DlcUpdateSkipStore",
    "client/update/DlcUpdateVersion",
    "core/DlcApplier",
    "core/DlcApplyMode",
    "core/DlcConfig",
    "core/DlcConfigTypeSupport",
    "core/DlcDcConfigIO",
    "core/DlcDcFormatUtil",
    "core/DlcDcDefinition",
    "core/DlcFileMatch",
    "core/DlcItem",
    "core/DlcItemFiles",
    "core/DlcPathUtil",
    "core/DlcPaths",
    "core/DlcResourceHistory",
    "core/DlcRuntimeState",
    "core/DlcScanner",
    "core/DlcStateConfig",
    "core/DlcTmpPaths",
    "core/DlcTooltipLoader",
    "core/DlcType",
    "platform/DlcPlatformServices",
    "platform/PlatformServices",

)

if (currentTarget == null) {
    val artifacts = bootstrapTargets.map { target ->
        val archive = distributionRoot.resolve(target.loader)
            .resolve("DLC-Manager-bootstrap-${target.loader}-${target.minecraft}-${project.version}.jar")
        target to archive
    }
    bootstrapTargets.groupBy { it.loader }.forEach { (loader, targets) ->
        tasks.register("${loader}BootstrapJar") {
            group = "build"
            description = "Build all $loader early-loading bootstrap artifacts."
            dependsOn(targets.map { ":${it.node}:bootstrapJar" })
            outputs.files(artifacts.filter { it.first.loader == loader }.map { it.second })
        }
    }
    tasks.assemble { dependsOn(bootstrapTargets.map { ":${it.node}:bootstrapJar" }) }
    tasks.test {
        dependsOn(bootstrapTargets.map { ":${it.node}:test" })
    }
} else {
    val target = currentTarget
    val stonecutter = requireNotNull(stonecutterBuild)
    stonecutter.constants {
        put("forge", target.loader == "forge")
        put("neoforge", target.loader == "neoforge")
    }
    val launcher = sourceSets.create("launcher")
    val launcherJson = configurations.create("launcherJson")
    launcher.compileClasspath += launcherJson
    val bootstrapLibraries = configurations.create("bootstrapLibraries")
    val neoForgeModLauncher = configurations.create("neoForgeModLauncher")
    val forgeLocatorApi = configurations.create("forgeLocatorApi")
    val neoForgeLocatorApi = configurations.create("neoForgeLocatorApi")
    neoForgeLocatorApi.extendsFrom(neoForgeModLauncher)
    neoForgeLocatorApi.attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)

    val targetJava = when (target.minecraft) {
        "26.1" -> 25
        "1.21.1" -> 21
        else -> 17
    }
    java.toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava))
    configurations.compileClasspath {
        attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJava)
    }
    configurations.runtimeClasspath {
        attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJava)
    }
    configurations.testCompileClasspath {
        attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJava)
    }
    configurations.testRuntimeClasspath {
        attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJava)
    }

    configurations.named(launcher.compileClasspathConfigurationName) {
        extendsFrom(configurations.compileOnly.get())
        attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJava)
    }

    launcher.java.setSrcDirs(listOf(stonecutter.tasks.generatedSourcesDir.dir("launcher")))
    tasks.named<JavaCompile>(launcher.compileJavaTaskName) {
        dependsOn("stonecutterGenerateLauncher")
        javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(targetJava)) })
        options.release.set(17)
    }
    tasks.matching { it.name == "compileLauncherKotlin" }.configureEach {
        dependsOn("stonecutterGenerateLauncher")
    }

    sourceSets.main {
        compileClasspath += launcher.output
        runtimeClasspath += launcher.output
        if (kotlinEnabled) resources.srcDir(rootProject.layout.projectDirectory.dir("src/kotlinRuntime/resources"))
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
                kotlin.exclude("com/cozooo/dlc_manager/client/server/**")
                kotlin.include(listOf("DlcBootstrapService", "DlcModPayload").map { "com/cozooo/dlc_manager/bootstrap/$it.kt" })
                kotlin.include(sharedClasses.map { "com/cozooo/dlc_manager/$it.kt" })
            }
        }
    }

    tasks.named<ProcessResources>("processResources") {
        from(managerRoot.resolve("src/main/resources/assets/dlc_manager/lang")) {
            include("en_us.json", "zh_cn.json")
            into("assets/dlc_manager/lang")
        }
        from(managerRoot.resolve("src/main/resources/META-INF/dlc-manager/dlc-definition.json")) {
            into("META-INF/dlc-manager")
            rename { "dlc-definition.json" }
        }
    }

    tasks.named<ProcessResources>(launcher.processResourcesTaskName) {
        from(managerRoot.resolve("src/main/resources/assets/dlc_manager/lang")) {
            include("en_us.json", "zh_cn.json")
            into("assets/dlc_manager/lang")
        }
    }

    sourceSets.test {
        compileClasspath += launcher.output
        runtimeClasspath += launcher.output + launcherJson
        if (stonecutter.current.isActive) {
            java.srcDir(rootProject.layout.projectDirectory.dir("src/test/java"))
        } else {
            java.srcDir(stonecutter.tasks.generatedSourcesDir.dir("test/java"))
            tasks.named(compileJavaTaskName) { dependsOn("stonecutterGenerateTest") }
        }
        java.srcDir(managerRoot.resolve("src/test/java"))
        java.include("com/cozooo/dlc_manager/bootstrap/**")
        java.include("com/cozooo/dlc_bootstrap/launch/**")
    }

    dependencies {
        add(launcherJson.name, "com.google.code.gson:gson:2.10.1")
        if (kotlinEnabled) add(bootstrapLibraries.name, "org.jetbrains.kotlin:kotlin-stdlib:$kotlinStdlibVersion") { isTransitive = false }
        if (kotlinEnabled && target.minecraft == "26.1") {
            add(bootstrapLibraries.name, "org.jetbrains.kotlin:kotlin-reflect:$kotlinStdlibVersion") { isTransitive = false }
        }
        implementation("com.cozooo.dlc_manager:dlc_fileops_helper:${managerProperties.getProperty("file_ops_helper_version")}")
        implementation("com.cozooo:dclanguage:0.1.1")
        implementation("com.google.code.gson:gson:2.10.1")
        val modLauncherVersion = when (target.node) {
            "1.18.2-forge" -> "9.0.24"
            "1.19.2-forge" -> "10.0.8"
            "1.20.1-forge" -> "10.0.9"
            "1.21.1-neoforge" -> "11.0.3"
            "26.1-neoforge" -> "11.0.5"
            else -> error("Unknown DLC bootstrap target: ${target.node}")
        }
        val secureJarHandlerVersion = when (target.node) {
            "1.18.2-forge" -> "1.0.3"
            "1.19.2-forge" -> "2.1.4"
            "1.20.1-forge" -> "2.1.10"
            "1.21.1-neoforge" -> "3.0.7"
            "26.1-neoforge" -> "3.0.8"
            else -> error("Unknown DLC bootstrap target: ${target.node}")
        }
        compileOnly("cpw.mods:modlauncher:$modLauncherVersion") { isTransitive = false }
        compileOnly("cpw.mods:securejarhandler:$secureJarHandlerVersion") { isTransitive = false }
        testImplementation("cpw.mods:modlauncher:$modLauncherVersion") { isTransitive = false }
        testImplementation("org.junit.jupiter:junit-jupiter:${managerProperties.getProperty("junit_jupiter_version")}")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        if (target.loader == "forge") {
            val forgeLoaderVersion = when (target.minecraft) {
                "1.18.2" -> "1.18.2-40.2.0"
                "1.19.2" -> "1.19.2-43.2.0"
                "1.20.1" -> "1.20.1-47.2.0"
                else -> error("Unknown Forge DLC bootstrap target: ${target.node}")
            }
            val forgeSpiVersion = when (target.minecraft) {
                "1.18.2" -> "4.0.15-4.x"
                "1.19.2" -> "6.0.0"
                "1.20.1" -> "7.0.1"
                else -> error("Unknown Forge DLC bootstrap target: ${target.node}")
            }
            compileOnly("net.minecraftforge:fmlloader:$forgeLoaderVersion") { isTransitive = false }
            compileOnly("net.minecraftforge:forgespi:$forgeSpiVersion") { isTransitive = false }
            testRuntimeOnly("net.minecraftforge:fmlloader:$forgeLoaderVersion")
        } else {
            val neoForgeLoaderVersion = when (target.minecraft) {
                "1.21.1" -> "4.0.42"
                "26.1" -> "11.0.15"
                else -> error("Unknown NeoForge DLC bootstrap target: ${target.node}")
            }
            compileOnly("net.neoforged.fancymodloader:loader:$neoForgeLoaderVersion") { isTransitive = false }
            if (target.minecraft == "26.1") {
                compileOnly("net.neoforged:mergetool:2.0.7:api")
            }
        }
        add(forgeLocatorApi.name, "net.minecraftforge:fmlloader:1.20.1-47.2.0")
        add(forgeLocatorApi.name, "net.minecraftforge:forgespi:7.0.1")
        add(neoForgeLocatorApi.name, "net.neoforged.fancymodloader:loader:4.0.42") { isTransitive = false }
        add(neoForgeLocatorApi.name, "cpw.mods:securejarhandler:3.0.8") { isTransitive = false }
        add(neoForgeLocatorApi.name, "org.slf4j:slf4j-api:2.0.0") { isTransitive = false }
        add(neoForgeModLauncher.name, "cpw.mods:modlauncher:11.0.5") { isTransitive = false }
    }

    val profileDir = layout.buildDirectory.dir("profiles")
    val runtimeServicesDir = layout.buildDirectory.dir("runtime-services")
    val runtimeServices = tasks.register("runtimeServices") {
        outputs.dir(runtimeServicesDir)
        doLast {
            val directory = runtimeServicesDir.get().asFile.apply { deleteRecursively(); mkdirs() }
            directory.resolve("META-INF/services/com.cozooo.dlc_bootstrap.launch.DlcBootstrapEntrypoint").apply {
                parentFile.mkdirs()
                writeText("com.cozooo.dlc_bootstrap.internal.bootstrap.DlcBootstrapService\n")
            }
        }
    }
    val profile = tasks.register("profile") {
        outputs.dir(profileDir)
        inputs.property("loader", target.loader)
        inputs.property("minecraft", target.minecraft)
        inputs.property("kotlinEnabled", kotlinEnabled)
        inputs.files(bootstrapLibraries)
        doLast {
            val directory = profileDir.get().asFile.apply { deleteRecursively(); mkdirs() }
            val service = if (target.loader == "forge") "net.minecraftforge.forgespi.locating.IModLocator" else "net.neoforged.neoforgespi.locating.IModFileCandidateLocator"
            val provider = if (target.loader == "forge") "DlcForgePayloadBridge" else "DlcNeoForgePayloadBridge"
            directory.resolve("META-INF/services/$service").apply { parentFile.mkdirs() }.writeText("com.cozooo.dlc_bootstrap.launch.$provider\n")
            directory.resolve("META-INF/services/cpw.mods.modlauncher.api.ITransformationService")
                .writeText("com.cozooo.dlc_bootstrap.launch.DlcBootstrapBridge\n")
            val libraries = bootstrapLibraries.files.sortedBy { it.name }
            directory.resolve("dlc-bootstrap.properties").writeText(
                "loader=${target.loader}\nminecraft=${target.minecraft}\nruntimeLibraries=${libraries.joinToString(",") { "META-INF/jarjar/${it.name}" }}\n"
            )
            if (kotlinEnabled) {
                directory.resolve("META-INF/jarjar/metadata.json").apply { parentFile.mkdirs() }.writeText(
                    libraries.joinToString(prefix = "{\"jars\":[", postfix = "]}") { library ->
                        val artifact = library.name.removeSuffix("-$kotlinStdlibVersion.jar")
                        """{"identifier":{"group":"org.jetbrains.kotlin","artifact":"$artifact"},"version":{"range":"[$kotlinStdlibVersion,$kotlinStdlibUpperBound)","artifactVersion":"$kotlinStdlibVersion"},"path":"META-INF/jarjar/${library.name}"}"""
                    }
                )
            }
            directory.resolve("META-INF/NOTICE.txt").writeText(
                "DLC Manager early installation service: bundled third-party components.\n" +
                    "Gson 2.10.1 (Apache-2.0): META-INF/dlc-manager/licenses/GSON-COPYRIGHT.txt\n" +
                    (if (kotlinEnabled) "Kotlin JVM Standard Library $kotlinStdlibVersion: META-INF/dlc-manager/licenses/kotlin/ATTRIBUTION.txt\n" else "") +
                    "License files apply to the identified third-party components; DLC Manager retains its own license.\n"
            )
        }
    }
    val runtimeJar = tasks.register<ShadowJar>("runtimeJar") {
        dependsOn(tasks.classes, profile, runtimeServices)
        from(sourceSets.main.get().output) {
            include("**/*.class", "**/*.kotlin_module", "META-INF/**", "assets/dlc_manager/lang/*.json")
        }
        from(profileDir)
        from(runtimeServicesDir)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains:annotations:.*"))
        }
        archiveFileName.set("DLC-Manager-runtime-${target.loader}-${target.minecraft}-${project.version}.jar")
        destinationDirectory.set(distributionRoot.resolve(target.loader))
        relocate("com.cozooo.dlc_manager", "com.cozooo.dlc_bootstrap.internal")
        relocate("com.cozooo.dlc_fileops_helper", "com.cozooo.dlc_bootstrap.internal.fileops_helper")
        relocate("com.cozooo.dclanguage", "com.cozooo.dlc_bootstrap.internal.dclanguage")
        relocate("com.google.gson", "com.cozooo.dlc_bootstrap.internal.gson")
        exclude("META-INF/jarjar/**", "META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "org/jetbrains/annotations/**")
    }
    val launcherJar = tasks.register<ShadowJar>("launcherJar") {
        dependsOn(launcher.classesTaskName)
        configurations = listOf(launcherJson)
        relocate("com.google.gson", "com.cozooo.dlc_bootstrap.launch.internal.gson")
        exclude("META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
        from(launcher.output) {
            include("**/*.class", "assets/dlc_manager/lang/*.json")
        }
        archiveFileName.set("bootstrap-launcher.jar")
        destinationDirectory.set(layout.buildDirectory.dir("launcher"))
    }
    val bootstrapJar = tasks.register<org.gradle.jvm.tasks.Jar>("bootstrapJar") {
        dependsOn(launcherJar, runtimeJar, profile)
        from(zipTree(launcherJar.flatMap { it.archiveFile })) {
            exclude("META-INF/MANIFEST.MF", "META-INF/dlc-manager/LICENSE")
        }
        from(profileDir)
        from(rootProject.layout.projectDirectory.dir("src/main/resources")) { include("META-INF/dlc-manager/licenses/**") }
        if (kotlinEnabled) from(rootProject.layout.projectDirectory.dir("src/kotlinRuntime/resources"))
        from(runtimeJar.flatMap { it.archiveFile }) {
            into("META-INF/dlc-manager")
            rename { "early.jar" }
        }
        from(bootstrapLibraries) { into("META-INF/jarjar") }
        exclude("**/${if (target.loader == "forge") "DlcNeoForgePayloadBridge" else "DlcForgePayloadBridge"}*.class")
        archiveFileName.set("DLC-Manager-bootstrap-${target.loader}-${target.minecraft}-${project.version}.jar")
        destinationDirectory.set(distributionRoot.resolve(target.loader))
        manifest.attributes("FMLModType" to "LIBRARY", "Automatic-Module-Name" to "dlc.manager.bootstrap.${target.loader}")
    }

    tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
        from(managerRoot.resolve("LICENSE")) { into("META-INF/dlc-manager") }
    }
    tasks.shadowJar { enabled = false }
    tasks.assemble { dependsOn(bootstrapJar) }
    tasks.test {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
        jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        inputs.files(neoForgeModLauncher, forgeLocatorApi, neoForgeLocatorApi, bootstrapLibraries)
        doFirst {
            systemProperty("bootstrapTestTarget", target.node)
            val kotlinStdlib = if (target.minecraft == "26.1") {
                bootstrapLibraries.files.find { it.name.startsWith("kotlin-stdlib-") }
            } else {
                bootstrapLibraries.files.singleOrNull()
            }
            systemProperty("kotlinStdlibJar", kotlinStdlib?.absolutePath ?: "")
            systemProperty("neoForgeModLauncher", neoForgeModLauncher.singleFile.absolutePath)
            systemProperty("forgeLocatorApi", forgeLocatorApi.asPath)
            systemProperty("neoforgeLocatorApi", neoForgeLocatorApi.asPath)
            bootstrapTargets.forEach { candidate ->
                val propertyName = when (candidate.loader to candidate.minecraft) {
                    "forge" to "1.20.1" -> "forgeBootstrapJar"
                    "neoforge" to "1.21.1" -> "neoforgeBootstrapJar"
                    else -> "${candidate.loader}BootstrapJar${candidate.minecraft.replace(".", "_")}Artifact"
                }
                systemProperty(
                    propertyName,
                    distributionRoot.resolve(candidate.loader)
                        .resolve("DLC-Manager-bootstrap-${candidate.loader}-${candidate.minecraft}-${project.version}.jar")
                        .absolutePath,
                )
            }
        }
        dependsOn(bootstrapTargets.map { ":${it.node}:bootstrapJar" })
    }
}
