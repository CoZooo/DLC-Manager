import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import gg.meza.stonecraft.extension.ModSettingsExtension
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.FileCollection
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File
import java.util.Properties

val DLC_MOD_ID = "dlc_manager"
val DLC_MOD_NAME = "DLC Manager"
val DLC_MOD_GROUP = "com.cozooo.dlc_manager"
val DLC_MOD_AUTHOR = "CoZooo"
val DLC_MOD_DESCRIPTION = "Manage optional DLC content (mods, resource packs, data packs) in-game."

buildscript {
    repositories {
        maven("https://maven.minecraftforge.net/")
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("net.minecraftforge.gradle:ForgeGradle:6.0.54")
    }
}

plugins {
    id("gg.meza.stonecraft") apply false
    id("dev.architectury.loom") apply false
    id("com.gradleup.shadow")
    id("org.jetbrains.kotlin.jvm") apply false
    id("maven-publish")
    base
}

fun discoverStoneNodes(root: File): List<String> =
    root.resolve("versions")
        .listFiles { file -> file.isDirectory && file.resolve("gradle.properties").isFile }
        ?.map { it.name }
        ?.sorted()
        ?: emptyList()

val stoneNodes = discoverStoneNodes(rootDir)

val dlcFixedModProperties = mapOf(
    "mod_id" to DLC_MOD_ID,
    "mod_name" to DLC_MOD_NAME,
    "mod_group" to DLC_MOD_GROUP,
    "mod_author" to DLC_MOD_AUTHOR,
    "mod_description" to DLC_MOD_DESCRIPTION,
    "mod.id" to DLC_MOD_ID,
    "mod.name" to DLC_MOD_NAME,
    "mod.group" to DLC_MOD_GROUP,
    "mod.author" to DLC_MOD_AUTHOR,
    "mod.version" to prop("mod_version"),
    "mod.description" to DLC_MOD_DESCRIPTION,
)

dlcFixedModProperties.forEach { (key, value) ->
    extensions.extraProperties[key] = value
}

apply(plugin = "gg.meza.stonecraft")

fun stoneNodeLoader(nodeName: String): String =
    nodeName.substringAfterLast("-")

fun stoneNodeMinecraftVersion(nodeName: String): String =
    nodeName.substringBeforeLast("-")

fun Project.prop(name: String): String =
    findProperty(name)?.toString()
        ?: rootProject.findProperty(name)?.toString()
        ?: dlcFixedModProperties[name]
        ?: error("Missing Gradle property: $name")

fun Project.optionalProp(name: String): String? =
    findProperty(name)?.toString() ?: rootProject.findProperty(name)?.toString()

fun Project.booleanProp(name: String, fallback: Boolean = false): Boolean =
    optionalProp(name)?.toBoolean() ?: fallback

fun Project.publishEnv(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

fun Project.modPublishReleaseType(): ReleaseType =
    ReleaseType.of(prop("mod_publish_release_type"))

fun Project.modPublishChangelog(): String =
    optionalProp("mod_publish_changelog")
        ?: publishEnv("MOD_PUBLISH_CHANGELOG")
        ?: readVersionChangelog(prop("mod_version"))

fun Project.readVersionChangelog(version: String): String {
    val changelogFile = rootProject.file("changelog.md")
    require(changelogFile.isFile) {
        "Missing changelog.md. Add a ## $version section or set -Pmod_publish_changelog=..."
    }
    val text = changelogFile.readText()
    val escapedVersion = Regex.escape(version)
    val header = Regex("""(?m)^##[ \t]+\[?v?$escapedVersion\]?(?:[ \t]+.*)?$""")
    val match = header.find(text)
        ?: error("Missing changelog.md section for version $version.")
    val nextHeader = Regex("""(?m)^##[ \t]+""").find(text, match.range.last + 1)
    val endIndex = nextHeader?.range?.first ?: text.length
    val body = text.substring(match.range.last + 1, endIndex).trim()
    require(body.isNotBlank()) {
        "changelog.md section for version $version is empty."
    }
    return normalizePublishChangelogHeadings(body)
}

fun normalizePublishChangelogHeadings(changelog: String): String =
    changelog
        .lineSequence()
        .map { line ->
            val match = Regex("""^(#{3,6})([ \t]+.+)$""").matchEntire(line)
            if (match == null) {
                line
            } else {
                "#".repeat(match.groupValues[1].length - 2) + match.groupValues[2]
            }
        }
        .joinToString("\n")

fun modPublishLoaderName(loaderName: String): String =
    when (loaderName) {
        "neoforge" -> "neoforge"
        else -> loaderName
    }

fun modPublishLoaderDisplayName(loaderName: String): String =
    when (loaderName) {
        "fabric" -> "Fabric"
        "forge" -> "Forge"
        "neoforge" -> "NeoForge"
        else -> loaderName.replaceFirstChar { it.uppercase() }
    }

fun Project.modPublishDisplayName(loaderName: String, minecraftVersion: String): String =
    "${prop("mod_name")} ${prop("mod_version")} (${modPublishLoaderDisplayName(loaderName)} $minecraftVersion)"

fun Project.configureLocalDevMods(loaderName: String, minecraftVersion: String) {
    fun devModTree(dir: File) = fileTree(dir) {
        include("**/*.jar")
    }

    val rootRunDir = rootProject.file("run/$loaderName/$minecraftVersion")
    val sharedDevModsDir = rootRunDir.resolve("devmods")
    val clientDevModsDir = rootRunDir.resolve("client/devmods")
    val serverDevModsDir = rootRunDir.resolve("server/devmods")
    listOf(sharedDevModsDir, clientDevModsDir, serverDevModsDir).forEach(File::mkdirs)

    val sharedDevModJars = files(devModTree(sharedDevModsDir))
    val clientDevModJars = files(devModTree(clientDevModsDir))
    val serverDevModJars = files(devModTree(serverDevModsDir))
    val devModJars = files(sharedDevModJars, clientDevModJars, serverDevModJars)

    fun isDevModArtifact(file: File, devModFiles: FileCollection): Boolean =
        devModFiles.files.any { devModFile ->
            val fileName = devModFile.name
            val baseName = fileName.removeSuffix(".jar")
            file.absolutePath == devModFile.absolutePath || file.name == fileName || file.name.contains(baseName)
        }

    val localRuntimeConfig = configurations.findByName("modLocalRuntime")
    if (localRuntimeConfig != null) {
        dependencies.add(localRuntimeConfig.name, devModJars)
    } else {
        configurations.findByName("modRuntimeOnly")?.let { runtimeConfig ->
            dependencies.add(runtimeConfig.name, devModJars)
        }
    }

    tasks.matching { it.name == "runClient" }.configureEach {
        if (this is JavaExec) {
            classpath = classpath.filter { file -> !isDevModArtifact(file, serverDevModJars) }
        }
    }
    tasks.matching { it.name == "runServer" }.configureEach {
        if (this is JavaExec) {
            classpath = classpath.filter { file -> !isDevModArtifact(file, clientDevModJars) }
        }
    }
}

repositories {
    mavenCentral()
}

if (!rootProject.extensions.extraProperties.has("dlcRootBuildTasksConfigured")) {
    rootProject.extensions.extraProperties.set("dlcRootBuildTasksConfigured", true)
    rootProject.run {
    tasks.withType<GradleBuild>().configureEach {
        startParameter.projectProperties.putAll(gradle.startParameter.projectProperties)
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(stoneNodes.map { ":$it:check" })
    }

    tasks.matching { it.name == "build" }.configureEach {
        dependsOn(stoneNodes.map { ":$it:remapJar" })
    }

    if (tasks.findByName("test") == null) {
        tasks.register("test") {
            group = "verification"
            description = "Run all tests."
            dependsOn(stoneNodes.map { ":$it:test" })
        }
    } else {
        tasks.named("test") {
            dependsOn(stoneNodes.map { ":$it:test" })
        }
    }

    tasks.named("test") {
        dependsOn(gradle.includedBuild("dlcBootstrap").task(":test"))
    }

    fun registerIncludedBuildTask(name: String, groupName: String, descriptionText: String, buildName: String, taskPath: String) {
        tasks.register(name) {
            group = groupName
            description = descriptionText
            dependsOn(gradle.includedBuild(buildName).task(taskPath))
        }
    }

    tasks.register<GradleBuild>("helperBuild") {
        group = "build"
        description = "Build helper library."
        dir = file("tools/file-ops-helper")
        tasks = listOf("build")
    }
    tasks.register<GradleBuild>("helperCheck") {
        group = "verification"
        description = "Compile-check helper library."
        dir = file("tools/file-ops-helper")
        tasks = listOf("check")
    }
    tasks.register<GradleBuild>("helperClean") {
        group = "build"
        description = "Clean helper library outputs."
        dir = file("tools/file-ops-helper")
        tasks = listOf("clean")
    }
    tasks.register<GradleBuild>("helperPublish") {
        group = "publishing"
        description = "Publish helper library to Maven Central Portal."
        dir = file("tools/file-ops-helper")
        tasks = listOf("publishToMavenCentral")
    }
    tasks.register("helperPublishCentral") {
        group = "publishing"
        description = "Publish helper library to Maven Central Portal."
        dependsOn("helperPublish")
    }

    registerIncludedBuildTask("javafxManagerBuild", "build", "Build the JavaFX DLC Manager desktop application.", "dlcJavafxManager", ":build")
    registerIncludedBuildTask("javafxManagerTest", "verification", "Run JavaFX DLC Manager tests.", "dlcJavafxManager", ":test")
    registerIncludedBuildTask("javafxManagerRun", "application", "Run the JavaFX DLC Manager desktop application.", "dlcJavafxManager", ":run")
    registerIncludedBuildTask("javafxManagerJarWin", "distribution", "Build the JavaFX DLC Manager runnable jar for Windows.", "dlcJavafxManager", ":jarWin")
    registerIncludedBuildTask("javafxManagerJarLinux", "distribution", "Build the JavaFX DLC Manager runnable jar for Linux x64.", "dlcJavafxManager", ":jarLinux")
    registerIncludedBuildTask("javafxManagerJarLinuxAarch64", "distribution", "Build the JavaFX DLC Manager runnable jar for Linux aarch64.", "dlcJavafxManager", ":jarLinuxAarch64")
    registerIncludedBuildTask("javafxManagerJarMacX64", "distribution", "Build the JavaFX DLC Manager runnable jar for macOS x64.", "dlcJavafxManager", ":jarMacX64")
    registerIncludedBuildTask("javafxManagerJarMacAarch64", "distribution", "Build the JavaFX DLC Manager runnable jar for macOS aarch64.", "dlcJavafxManager", ":jarMacAarch64")
    registerIncludedBuildTask("javafxManagerJarAll", "distribution", "Build the JavaFX DLC Manager runnable jars for all supported platforms.", "dlcJavafxManager", ":jarAll")
    registerIncludedBuildTask("javafxManagerInstaller", "distribution", "Build the JavaFX DLC Manager installer for the current platform.", "dlcJavafxManager", ":jpackageInstaller")
    registerIncludedBuildTask("javafxManagerInstallers", "distribution", "Build both JavaFX DLC Manager installer formats for the current platform.", "dlcJavafxManager", ":jpackageInstallers")
    registerIncludedBuildTask("javafxManagerNotarizedInstallers", "distribution", "Build, sign, and notarize both macOS JavaFX installer formats.", "dlcJavafxManager", ":notarizeInstallers")

    tasks.register("publishModPlatforms") {
        group = "publishing"
        description = "Publish all mod artifacts to configured mod platforms."
        dependsOn(stoneNodes.map { ":$it:publishMods" })
        doFirst {
            val dryRun = booleanProp("mod_publish_dry_run", false) ||
                (publishEnv("MOD_PUBLISH_DRY_RUN")?.toBooleanStrictOrNull() ?: false)
            if (!dryRun && publishEnv("MODRINTH_TOKEN").isNullOrBlank() && publishEnv("CURSEFORGE_TOKEN").isNullOrBlank()) {
                error("Set MODRINTH_TOKEN or CURSEFORGE_TOKEN, or use -Pmod_publish_dry_run=true.")
            }
        }
    }
    }
}

if (stoneNodes.contains(project.name)) {
    val nodeName = project.name
    val loaderName = stoneNodeLoader(nodeName)
    val minecraftVersion = stoneNodeMinecraftVersion(nodeName)
    val kotlinEnabled = booleanProp("enable_kotlin", true)
    val kotlinExcludeJavaCompile = kotlinEnabled && booleanProp("kotlin_exclude_java_compile", false)
    val publishDryRun = booleanProp("mod_publish_dry_run", false) ||
        (publishEnv("MOD_PUBLISH_DRY_RUN")?.toBooleanStrictOrNull() ?: false)
    val modrinthToken = publishEnv("MODRINTH_TOKEN")
    val curseforgeToken = publishEnv("CURSEFORGE_TOKEN")

    val nodeBuildDirName = if (kotlinEnabled) nodeName else "java-only/$nodeName"
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir(nodeBuildDirName))

    val javaVersion = prop("java_version").toInt()
    val javaToolchains = extensions.getByType<JavaToolchainService>()

    group = prop("mod_group")
    version = prop("mod_version")

    repositories {
        maven {
            name = "ApricityUI"
            url = uri("https://maven.sighs.cc/repository/maven-public/")
            content {
                includeGroup("com.sighs")
            }
        }
        maven {
            name = "Minecraft Libraries"
            url = uri("https://libraries.minecraft.net")
            content {
                includeGroup("org.lwjgl")
                includeGroup("com.mojang")
            }
        }
        maven {
            name = "ParchmentMC"
            url = uri("https://maven.parchmentmc.org")
            content {
                includeGroup("org.parchmentmc.data")
            }
        }
        maven {
            name = "Architectury"
            url = uri("https://maven.architectury.dev/")
            content {
                includeGroup("dev.architectury")
            }
        }
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
            content {
                includeGroup("net.fabricmc")
                includeGroup("net.fabricmc.fabric-api")
            }
        }
        maven {
            name = "Forge"
            url = uri("https://maven.minecraftforge.net/")
            content {
                includeGroup("net.minecraftforge")
                includeGroup("net.minecraftforge.gradle")
                includeGroup("cpw.mods")
            }
        }
        maven {
            name = "NeoForge"
            url = uri("https://maven.neoforged.net/releases/")
            content {
                includeGroupByRegex("net\\.neoforged(\\..*)?")
                includeGroup("cpw.mods")
                includeGroup("net.neoforged.gradle")
            }
        }
        if (kotlinEnabled) {
            maven {
                name = "Kotlin for Forge"
                url = uri("https://thedarkcolour.github.io/KotlinForForge/")
                content {
                    includeGroup("thedarkcolour")
                }
            }
        }
        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
            content {
                includeGroup("maven.modrinth")
            }
        }
        maven {
            name = "Terraformers"
            url = uri("https://maven.terraformersmc.com")
            content {
                includeGroup("com.terraformersmc")
            }
        }
        maven {
            name = "CurseMaven"
            url = uri("https://cursemaven.com")
            content {
                includeGroup("curse.maven")
            }
        }
        mavenCentral()
    }
    repositories.withType<MavenArtifactRepository>().configureEach {
        when (url.toString().trimEnd('/')) {
            "https://libraries.minecraft.net" -> content {
                includeGroup("org.lwjgl")
                includeGroup("com.mojang")
            }
            "https://maven.parchmentmc.org" -> content {
                includeGroup("org.parchmentmc.data")
            }
            "https://maven.architectury.dev" -> content {
                includeGroup("dev.architectury")
            }
            "https://maven.fabricmc.net" -> content {
                includeGroup("net.fabricmc")
                includeGroup("net.fabricmc.fabric-api")
            }
            "https://maven.minecraftforge.net" -> content {
                includeGroup("net.minecraftforge")
                includeGroup("net.minecraftforge.gradle")
                includeGroup("cpw.mods")
            }
            "https://maven.neoforged.net/releases" -> content {
                includeGroupByRegex("net\\.neoforged(\\..*)?")
                includeGroup("cpw.mods")
                includeGroup("net.neoforged.gradle")
            }
            "https://thedarkcolour.github.io/KotlinForForge" -> content {
                includeGroup("thedarkcolour")
            }
            "https://api.modrinth.com/maven" -> content {
                includeGroup("maven.modrinth")
            }
            "https://maven.terraformersmc.com" -> content {
                includeGroup("com.terraformersmc")
            }
            "https://cursemaven.com" -> content {
                includeGroup("curse.maven")
            }
            "https://repo.maven.apache.org/maven2" -> content {
                excludeGroup("curse.maven")
                excludeGroup("dev.architectury")
                excludeGroup("maven.modrinth")
                excludeGroup("net.fabricmc")
                excludeGroup("net.fabricmc.fabric-api")
                excludeGroup("net.minecraftforge")
                excludeGroup("net.neoforged")
                excludeGroupByRegex("net\\.neoforged(\\..*)?")
                excludeGroup("org.parchmentmc.data")
                excludeGroup("thedarkcolour")
            }
        }
    }

    if (kotlinEnabled) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(javaVersion)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(javaVersion)
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
    }
    tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
        exclude("rebel.xml")
        if (name == "sourcesJar") {
            from(rootProject.layout.projectDirectory.file("LICENSE")) { into("META-INF/dlc-manager") }
        }
    }
    val mixinTestJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    tasks.withType<Test>().configureEach {
        dependsOn(mixinTestJar)
        inputs.file(mixinTestJar)
        systemProperty("dlc_manager.test.loader", loaderName)
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Ddlc_manager.test.jar=${mixinTestJar.get().asFile.absolutePath}")
        })
        if (loaderName == "forge") {
            dependsOn("generateDLIConfig")
            val launchConfig = rootProject.layout.projectDirectory.file(".gradle/loom-cache/projects/$nodeName/launch.cfg")
            inputs.file(launchConfig)
            systemProperty("dlc_manager.test.launchConfig", launchConfig.asFile.absolutePath)
        }
    }

    tasks.withType<ProcessResources>().configureEach {
        notCompatibleWithConfigurationCache("StoneCraft processResources actions capture Project.")
        if (name == "processResources") {
            from(rootProject.layout.projectDirectory.file("LICENSE")) { into("META-INF/dlc-manager") }
            val updateDefinition = rootProject.layout.projectDirectory.file("src/main/resources/META-INF/dlc-manager/dlc-definition.json")
            inputs.file(updateDefinition)
            doLast {
                val target = destinationDir.resolve("META-INF/dlc-manager/dlc-definition.json")
                target.parentFile.mkdirs()
                updateDefinition.asFile.copyTo(target, overwrite = true)
            }
        }
    }

    val stonecutterBuild = extensions.getByType<StonecutterBuildExtension>()

    if (kotlinEnabled) {
        extensions.configure<KotlinJvmProjectExtension> {
            sourceSets.named("main") {
                kotlin.setSrcDirs(listOf(layout.projectDirectory.dir("src/main/kotlin")))
            }
        }
    }

    extensions.configure<SourceSetContainer> {
        named("main") {
            stonecutterBuild.tasks.configureSource(this)
        }
        named("test") {
            stonecutterBuild.tasks.configureSource(this)
        }
    }

    if (kotlinEnabled) {
        tasks.named("compileKotlin") {
            dependsOn("stonecutterGenerate")
        }
    }

    if (kotlinExcludeJavaCompile) {
        extensions.configure<SourceSetContainer> {
            named("main") {
                java.setSrcDirs(emptyList<File>())
            }
        }
        tasks.named<JavaCompile>("compileJava") {
            enabled = false
            setDependsOn(emptyList<Any>())
        }
        val clearMainJavaClasses = tasks.register<Delete>("clearMainJavaClasses") {
            delete(tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory })
        }
        tasks.named<Jar>("jar") {
            dependsOn(clearMainJavaClasses)
        }
    }

    val kffDependencyText = if (kotlinEnabled && (loaderName == "forge" || loaderName == "neoforge")) {
        """

        [[dependencies.${prop("mod_id")}]]
        modId="kotlinforforge"
        mandatory=true
        versionRange="${prop("kotlin_for_forge_version_range")}"
        ordering="NONE"
        side="BOTH"
        """.trimIndent()
    } else {
        ""
    }
    configure<ModSettingsExtension> {
        runDirectory = rootProject.layout.projectDirectory.dir("run/$loaderName/$minecraftVersion/client")
        testClientRunDirectory = rootProject.layout.projectDirectory.dir("run/$loaderName/$minecraftVersion/client")
        testServerRunDirectory = rootProject.layout.projectDirectory.dir("run/$loaderName/$minecraftVersion/server")
        generatedResources = layout.buildDirectory.dir("generated/resources").get()
        accessWidenerProcessing = false
        variableReplacements.set(
            mapOf<String, Any>(
                "mod_id" to prop("mod_id"),
                "mod_name" to prop("mod_name"),
                "mod_description" to prop("mod_description"),
                "mod_version" to prop("mod_version"),
                "mod_author" to prop("mod_author"),
                "curseforge_project_id" to prop("curseforge_project_id"),
                "modrinth_project_id" to prop("modrinth_project_id"),
                "mc_version" to prop("minecraft_version"),
                "mc_version_range" to prop("minecraft_version_range"),
                "java_version" to prop("java_version"),
                "fabric_loader_version" to optionalProp("fabric_loader_version").orEmpty(),
                "fabric_api_version" to optionalProp("fabric_api_version").orEmpty(),
                "fabric_api_version_range" to optionalProp("fabric_api_version_range").orEmpty(),
                "forge_loader_version_range" to optionalProp("forge_loader_version_range").orEmpty(),
                "forge_minecraft_version_range" to optionalProp("forge_minecraft_version_range").orEmpty(),
                "neoforge_loader_version_range" to optionalProp("neoforge_loader_version_range").orEmpty(),
                "neoforge_version_range" to optionalProp("neoforge_version_range").orEmpty(),
                "neoforge_minecraft_version_range" to optionalProp("neoforge_minecraft_version_range").orEmpty(),
                "kotlin_for_forge_dependency" to kffDependencyText,
                "apricity_ui_version" to prop("apricity_ui_version"),
                "pack_format" to prop("pack_format"),
            )
        )
    }

    if (loaderName == "fabric" && kotlinEnabled) {
        tasks.named<ProcessResources>("processResources") {
            inputs.property("fabricLanguageKotlinVersion", prop("fabric_language_kotlin_version"))
            doLast {
                val outputFile = destinationDir.resolve("fabric.mod.json")
                if (!outputFile.isFile) {
                    return@doLast
                }
                @Suppress("UNCHECKED_CAST")
                val json = JsonSlurper().parse(outputFile) as MutableMap<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val depends = json["depends"] as MutableMap<String, Any?>
                depends["fabric-language-kotlin"] = "*"
                val patched = JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n"
                if (outputFile.readText() != patched) {
                    outputFile.writeText(patched)
                }
            }
        }
    }

    extensions.configure<LoomGradleExtensionAPI> {
        if (loaderName == "forge") {
            forge.mixinConfig("dlc_manager.mixins.json")
        }
        runs.named("server") {
            runDirectory.set(rootProject.layout.projectDirectory.dir("run/$loaderName/$minecraftVersion/server"))
        }
    }

    configurations {
        maybeCreate("embedded")
        if (loaderName == "neoforge" && kotlinEnabled) {
            maybeCreate("kotlinForForgeModDiscovery")
        }
        if (loaderName == "neoforge") {
            named("testRuntimeClasspath") {
                exclude(group = "net.neoforged", module = "neoforge")
            }
        }
    }
    if (loaderName == "fabric") {
        configurations.findByName("modApi")?.dependencies?.removeIf {
            it.group == "net.fabricmc.fabric-api" &&
                it.name == "fabric-gametest-api-v1" &&
                it.version == prop("fabric_version")
        }
    }
    configureLocalDevMods(loaderName, minecraftVersion)

    fun addEmbedded(notation: String) {
        dependencies.add("implementation", notation)
        dependencies.add("include", notation)
    }

    listOf("modImplementation", "implementation", "include", "forgeRuntimeLibrary", "testImplementation", "testRuntimeOnly").forEach { configurations.maybeCreate(it) }
    dependencies {
        val modDependencyConfiguration = if (minecraftVersion == "26.1") "implementation" else "modImplementation"
        val architecturyDependency = when (loaderName) {
            "fabric" -> "dev.architectury:architectury-fabric:${prop("architectury_api_version")}"
            "forge" -> "dev.architectury:architectury-forge:${prop("architectury_api_version")}"
            "neoforge" -> "dev.architectury:architectury-neoforge:${prop("architectury_api_version")}"
            else -> error("Unsupported loader: $loaderName")
        }
        add(modDependencyConfiguration, architecturyDependency)

        val apricityDependency = "com.sighs:ApricityUI-$loaderName-$minecraftVersion:${prop("apricity_ui_version")}"
        add(modDependencyConfiguration, apricityDependency)

        if (loaderName == "fabric") {
            if (kotlinEnabled) {
                add(modDependencyConfiguration, "net.fabricmc:fabric-language-kotlin:${prop("fabric_language_kotlin_version")}")
            }
        }

        if (loaderName == "forge") {
            if (kotlinEnabled) {
                add("implementation", "thedarkcolour:kotlinforforge:${prop("kotlin_for_forge_version")}")
            }
        }

        if (loaderName == "neoforge") {
            if (kotlinEnabled) {
                add("kotlinForForgeModDiscovery", "thedarkcolour:kotlinforforge-neoforge:${prop("kotlin_for_forge_version")}") {
                    isTransitive = false
                }
                if (minecraftVersion == "26.1") {
                    val kotlinRuntimeVersion = optionalProp("kotlin_runtime_version")
                        ?: error("Missing Kotlin runtime version for ${nodeName}")
                    add(modDependencyConfiguration, "org.jetbrains.kotlin:kotlin-reflect:$kotlinRuntimeVersion")
                }
                add(modDependencyConfiguration, "thedarkcolour:kfflang-neoforge:${prop("kotlin_for_forge_version")}") {
                    isTransitive = false
                }
                add(modDependencyConfiguration, "thedarkcolour:kffmod-neoforge:${prop("kotlin_for_forge_version")}") {
                    isTransitive = false
                }
            }
        }

        addEmbedded("com.cozooo:dclanguage:0.1.1")
        addEmbedded("com.cozooo.dlc_manager:dlc_fileops_helper:${prop("file_ops_helper_version")}")
        addEmbedded("org.commonmark:commonmark:0.21.0")
        addEmbedded("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")

        if (loaderName == "forge" || loaderName == "neoforge") {
            add("forgeRuntimeLibrary", "com.cozooo:dclanguage:0.1.1")
            add("forgeRuntimeLibrary", "com.cozooo.dlc_manager:dlc_fileops_helper:${prop("file_ops_helper_version")}")
            add("forgeRuntimeLibrary", "org.commonmark:commonmark:0.21.0")
            add("forgeRuntimeLibrary", "org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
        }

        add("testImplementation", "org.junit.jupiter:junit-jupiter:${rootProject.prop("junit_jupiter_version")}")
        add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:${rootProject.prop("junit_jupiter_version")}")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        if (loaderName == "forge" && (minecraftVersion == "1.18.2" || minecraftVersion == "1.19.2")) {
            addEmbedded("org.joml:joml:1.10.5")
            add("forgeRuntimeLibrary", "org.joml:joml:1.10.5")
        }
    }

    if (loaderName == "forge" || loaderName == "neoforge") {
        val distributionJar = rootProject.layout.projectDirectory.file(
            "output/${rootProject.prop("mod_version")}/DLC-Manager-$loaderName-$minecraftVersion-${rootProject.prop("mod_version")}.jar"
        )
        val serviceBuild = if (kotlinEnabled) "build/dlc-bootstrap" else "build/java-only/dlc-bootstrap"
        val bootstrapJar = rootProject.layout.projectDirectory.file(
            "$serviceBuild/services/$loaderName/DLC-Manager-bootstrap-$loaderName-$minecraftVersion-${rootProject.prop("mod_version")}.jar"
        )
        val payloadTaskName = if (tasks.findByName("remapJar") != null) "remapJar" else "jar"
        val devPayload = if (payloadTaskName == "remapJar") {
            rootProject.layout.projectDirectory.file(
                "build/$nodeName/distribution/payload/DLC-Manager-$loaderName-$minecraftVersion-${rootProject.prop("mod_version")}.jar"
            )
        } else distributionJar
        listOf("client", "server").forEach { side ->
            val sideName = side.replaceFirstChar { it.uppercase() }
            val prepareDistribution = tasks.register<Copy>("prepare${sideName}DlcDistribution") {
                dependsOn(payloadTaskName, gradle.includedBuild("dlcBootstrap").task(":${loaderName}BootstrapJar"))
                from(bootstrapJar)
                into(rootProject.layout.projectDirectory.dir("run/$loaderName/$minecraftVersion/$side/mods"))
                doFirst {
                    destinationDir.listFiles()?.filter {
                        (it.name.startsWith("DLC-Manager-bootstrap-$loaderName-$minecraftVersion-") ||
                            it.name.startsWith("DLC-Manager-$loaderName-$minecraftVersion-")) &&
                            it.name.endsWith(".jar")
                    }?.forEach(File::delete)
                }
            }
            tasks.matching { it.name == "run$sideName" || it.name == "configureMinecraft$sideName" }.configureEach {
                dependsOn(prepareDistribution)
            }
        }
        tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
            if (this is JavaExec) {
                jvmArgs("-Ddlc_manager.dev.mod.path=${devPayload.asFile.absolutePath}")
                jvmArgs("-Ddlc_manager.distribution.path=${devPayload.asFile.absolutePath}")
            }
        }
        val mainSourceSet = extensions.getByType<SourceSetContainer>().named("main").get()
        extensions.configure<LoomGradleExtensionAPI> {
            runs.configureEach {
                mods {
                    maybeCreate(prop("mod_id")).apply {
                        modFiles.setFrom(mainSourceSet.output)
                        mainSourceSet.output.resourcesDir?.let { mainResourceDirectory.set(it) }
                    }
                }
            }
        }
    }

    if (loaderName == "neoforge" && kotlinEnabled) {
        val discoveryJarName = "kotlinforforge-neoforge.jar"
        val discoveryJar = layout.buildDirectory.file("kotlinForForgeModDiscovery/$discoveryJarName")
        val prepareKotlinForForgeModDiscovery = tasks.register<Copy>("prepareKotlinForForgeModDiscovery") {
            from(configurations.getByName("kotlinForForgeModDiscovery"))
            into(discoveryJar.map { it.asFile.parentFile })
            rename { discoveryJarName }
        }
        tasks.matching {
            it.name == "generateDLIConfig" || it.name == "runClient" || it.name == "runServer"
        }.configureEach {
            dependsOn(prepareKotlinForForgeModDiscovery)
        }
        extensions.configure<LoomGradleExtensionAPI> {
            runs.configureEach {
                mods {
                    maybeCreate("kotlinforforge").modFiles.from(files(discoveryJar).builtBy(prepareKotlinForForgeModDiscovery))
                }
            }
        }
    }

    tasks.named<Jar>("jar") {
        archiveClassifier.set("dev")
    }

    tasks.withType<ShadowJar>().configureEach {
        archiveClassifier.set("dev-shadow")
    }

    val distributionJar = rootProject.layout.projectDirectory.file(
        "output/${rootProject.prop("mod_version")}/DLC-Manager-$loaderName-$minecraftVersion-${rootProject.prop("mod_version")}.jar"
    )
    val earlyLoading = loaderName == "forge" || loaderName == "neoforge"
    val remappedJar: org.gradle.api.tasks.TaskProvider<out AbstractArchiveTask> = if (tasks.findByName("remapJar") != null) {
        tasks.named<RemapJarTask>("remapJar") {
            archiveFileName.set(distributionJar.asFile.name)
            if (earlyLoading) destinationDirectory.set(layout.buildDirectory.dir("distribution/payload"))
            else destinationDirectory.set(rootProject.layout.projectDirectory.dir("output/${rootProject.prop("mod_version")}"))
        }
    } else {
        tasks.named<Jar>("jar") {
            archiveFileName.set(distributionJar.asFile.name)
            destinationDirectory.set(rootProject.layout.projectDirectory.dir("output/${rootProject.prop("mod_version")}"))
        }
    }
    if (earlyLoading) {
        val serviceBuild = if (kotlinEnabled) "build/dlc-bootstrap" else "build/java-only/dlc-bootstrap"
        val serviceJar = rootProject.layout.projectDirectory.file(
            "$serviceBuild/services/$loaderName/DLC-Manager-bootstrap-$loaderName-$minecraftVersion-${rootProject.prop("mod_version")}.jar"
        )
        val packageDistribution = tasks.register<JavaExec>("packageDistribution") {
            group = "build"
            description = "Package the early service and main mod into one distributable JAR."
            dependsOn(remappedJar, gradle.includedBuild("dlcBootstrap").task(":${loaderName}BootstrapJar"))
            inputs.files(serviceJar, remappedJar.flatMap { it.archiveFile })
            outputs.file(distributionJar)
            classpath = files(serviceJar)
            javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
            mainClass.set("com.cozooo.dlc_bootstrap.launch.DlcDistributionAssembler")
            doFirst {
                setArgs(listOf(serviceJar.asFile.absolutePath, remappedJar.get().archiveFile.get().asFile.absolutePath,
                    distributionJar.asFile.absolutePath))
            }
            doLast {
                distributionJar.asFile.parentFile.listFiles()?.filter {
                    it.name.startsWith("DLC-Manager-bootstrap-$loaderName-$minecraftVersion-") && it.name.endsWith(".jar")
                }?.forEach(File::delete)
            }
        }
        if (remappedJar.name == "jar") {
            tasks.withType<Test>().configureEach { dependsOn(packageDistribution) }
        }
        remappedJar.configure { finalizedBy(packageDistribution) }
    }
    tasks.named("publishMods") {
        dependsOn(if (earlyLoading) "packageDistribution" else remappedJar)
    }

    configure<ModPublishExtension> {
        dryRun.set(publishDryRun)
        if (earlyLoading) file.set(tasks.named("packageDistribution").map { distributionJar })
        else file.set(remappedJar.flatMap { it.archiveFile })
        version.set("${rootProject.prop("mod_version")}-$loaderName-$minecraftVersion")
        displayName.set(rootProject.modPublishDisplayName(loaderName, minecraftVersion))
        changelog.set(rootProject.providers.provider { rootProject.modPublishChangelog() })
        type.set(rootProject.modPublishReleaseType())
        modLoaders.add(modPublishLoaderName(loaderName))

        if (!modrinthToken.isNullOrBlank() || publishDryRun) {
            modrinth {
                accessToken.set(modrinthToken ?: "")
                projectId.set(rootProject.prop("modrinth_project_id"))
                version.set(rootProject.prop("mod_version"))
                minecraftVersions.add(minecraftVersion)
                environment.set(CLIENT_AND_SERVER)
                when (loaderName) {
                    "fabric" -> {
                        requires("architectury-api", "fabric-language-kotlin")
                        requires("apricityui")
                    }
                    "forge" -> {
                        requires("architectury-api", "kotlin-for-forge")
                        requires("apricityui")
                    }
                    "neoforge" -> {
                        requires("architectury-api", "kotlin-for-forge")
                        requires("apricityui")
                    }
                }
            }
        }

        if (!curseforgeToken.isNullOrBlank() || publishDryRun) {
            curseforge {
                accessToken.set(curseforgeToken ?: "")
                projectId.set(rootProject.prop("curseforge_project_id"))
                minecraftVersions.add(minecraftVersion)
                client.set(true)
                server.set(true)
                javaVersions.add(JavaVersion.toVersion(javaVersion))
                when (loaderName) {
                    "fabric" -> {
                        requires("architectury-api", "fabric-language-kotlin")
                        requires("apricityui")
                    }
                    "forge" -> {
                        requires("architectury-api", "kotlin-for-forge")
                        requires("apricityui")
                    }
                    "neoforge" -> {
                        requires("architectury-api", "kotlin-for-forge")
                        requires("apricityui")
                    }
                }
            }
        }
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                components.findByName("java")?.let { from(it) }
                groupId = rootProject.prop("mod_group")
                artifactId = "$loaderName-$minecraftVersion"
                version = rootProject.prop("mod_version")
            }
        }
    }
}
