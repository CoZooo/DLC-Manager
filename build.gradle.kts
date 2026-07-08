import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import gg.meza.stonecraft.extension.ModSettingsExtension
import gg.meza.stonecraft.mod
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("gg.meza.stonecraft")
    id("com.gradleup.shadow")
    id("org.jetbrains.kotlin.jvm") apply false
    id("maven-publish")
    base
}

val stoneNodes = listOf(
    "1.20.1-fabric",
    "1.20.1-forge",
    "1.21.1-fabric",
    "1.21.1-neoforge",
)

fun Project.prop(name: String): String =
    findProperty(name)?.toString()
        ?: rootProject.findProperty(name)?.toString()
        ?: error("Missing Gradle property: $name")

fun Project.optionalProp(name: String): String? =
    findProperty(name)?.toString() ?: rootProject.findProperty(name)?.toString()

fun Project.booleanProp(name: String, fallback: Boolean = false): Boolean =
    optionalProp(name)?.toBoolean() ?: fallback

fun Project.loadProperties(file: File): Map<String, String> {
    if (!file.isFile) {
        return emptyMap()
    }
    val properties = Properties()
    file.inputStream().use(properties::load)
    return properties.entries.associate { it.key.toString() to it.value.toString() }
}

fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split(".").mapNotNull { it.toIntOrNull() }
    val rightParts = right.split(".").mapNotNull { it.toIntOrNull() }
    val size = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until size) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }
    return 0
}

fun evaluateCondition(expression: String, loaderName: String, minecraftVersion: String): Boolean =
    expression.split("||").any { orPart ->
        orPart.split("&&").all { rawTerm ->
            var term = rawTerm.trim()
            if (term.isEmpty()) {
                return@all true
            }
            val negated = term.startsWith("!")
            if (negated) {
                term = term.drop(1).trim()
            }
            val value = when (term) {
                "fabric", "forge", "neoforge" -> loaderName == term
                "forgeLike" -> loaderName == "forge" || loaderName == "neoforge"
                "fabricLike" -> loaderName == "fabric"
                else -> {
                    val match = Regex("""^(>=|<=|>|<|==|=)?\s*([0-9]+(?:\.[0-9]+)*)$""").matchEntire(term)
                        ?: error("Unsupported Stonecutter condition '$rawTerm'")
                    val operator = match.groupValues[1].ifBlank { "==" }
                    val compared = compareVersions(minecraftVersion, match.groupValues[2])
                    when (operator) {
                        ">" -> compared > 0
                        ">=" -> compared >= 0
                        "<" -> compared < 0
                        "<=" -> compared <= 0
                        "=", "==" -> compared == 0
                        else -> false
                    }
                }
            }
            if (negated) !value else value
        }
    }

fun preprocessStonecutterText(source: String, loaderName: String, minecraftVersion: String): String {
    data class Frame(var parentActive: Boolean, var branchMatched: Boolean, var currentActive: Boolean)

    val frames = mutableListOf<Frame>()
    val output = mutableListOf<String>()
    source.lineSequence().forEach { line ->
        val trimmed = line.trim()
        val ifMatch = Regex("""^//\?\s*if\s+(.+?)\s*\{$""").matchEntire(trimmed)
        val elseIfMatch = Regex("""^//\?\}\s*else\s+if\s+(.+?)\s*\{$""").matchEntire(trimmed)
        val elseMatch = Regex("""^//\?\}\s*else\s*\{$""").matchEntire(trimmed)
        val endMatch = Regex("""^//\?\}$""").matchEntire(trimmed)
        when {
            ifMatch != null -> {
                val parentActive = frames.lastOrNull()?.currentActive ?: true
                val active = parentActive && evaluateCondition(ifMatch.groupValues[1], loaderName, minecraftVersion)
                frames += Frame(parentActive, active, active)
            }
            elseIfMatch != null -> {
                val frame = frames.lastOrNull() ?: error("Stonecutter else-if without matching if")
                val active = frame.parentActive &&
                    !frame.branchMatched &&
                    evaluateCondition(elseIfMatch.groupValues[1], loaderName, minecraftVersion)
                frame.currentActive = active
                frame.branchMatched = frame.branchMatched || active
            }
            elseMatch != null -> {
                val frame = frames.lastOrNull() ?: error("Stonecutter else without matching if")
                val active = frame.parentActive && !frame.branchMatched
                frame.currentActive = active
                frame.branchMatched = true
            }
            endMatch != null -> {
                if (frames.isEmpty()) {
                    error("Stonecutter end without matching if")
                }
                frames.removeLast()
            }
            frames.lastOrNull()?.currentActive != false -> output += line
        }
    }
    check(frames.isEmpty()) { "Unclosed Stonecutter condition" }
    return output.joinToString(System.lineSeparator()) + System.lineSeparator()
}

fun Project.copyProcessedTree(sourceRoot: File, outputRoot: File, loaderName: String, minecraftVersion: String) {
    if (!sourceRoot.isDirectory) {
        return
    }
    fileTree(sourceRoot).matching { include("**/*") }.files.forEach { input ->
        if (input.isDirectory) {
            return@forEach
        }
        val relative = sourceRoot.toPath().relativize(input.toPath()).toString().replace(File.separatorChar, '/')
        val output = outputRoot.resolve(relative)
        output.parentFile.mkdirs()
        if (input.extension in setOf("java", "kt", "json", "toml", "mcmeta", "properties", "txt", "md")) {
            output.writeText(preprocessStonecutterText(input.readText(), loaderName, minecraftVersion))
        } else {
            input.copyTo(output, overwrite = true)
        }
    }
}

fun Project.javaSourcesReplacedByKotlin(kotlinSourceDirs: List<File>): List<String> =
    kotlinSourceDirs.flatMap { root ->
        if (!root.exists()) {
            emptyList()
        } else {
            fileTree(root).matching { include("**/*.kt") }.files.map { kotlinFile ->
                root.toPath().relativize(kotlinFile.toPath()).toString()
                    .replace(File.separatorChar, '/')
                    .replace(Regex("""\.kt$"""), ".java")
            }
        }
    }

fun Project.deleteKotlinClassesReplacedByJava(kotlinSourceDirs: List<File>, kotlinClassesDir: File) {
    if (!kotlinClassesDir.exists()) {
        return
    }
    javaSourcesReplacedByKotlin(kotlinSourceDirs).forEach { javaPath ->
        val classPath = javaPath.removeSuffix(".java")
        val slash = classPath.lastIndexOf('/')
        val classDir = if (slash >= 0) kotlinClassesDir.resolve(classPath.substring(0, slash)) else kotlinClassesDir
        val className = if (slash >= 0) classPath.substring(slash + 1) else classPath
        if (classDir.exists()) {
            delete(fileTree(classDir) {
                include("$className.class")
                include("$className\$*.class")
            })
        }
    }
}

fun Project.clearMainJavaClasses() {
    val compileJava = tasks.findByName("compileJava") as? JavaCompile ?: return
    delete(compileJava.destinationDirectory.get().asFile)
}

fun Project.runYGuardObfuscation(inputJar: File, outputJar: File, libDir: File) {
    require(inputJar.exists()) { "Input jar not found: $inputJar" }
    require(libDir.exists()) { "Library directory not found: $libDir" }

    outputJar.parentFile.mkdirs()
    val renameLogDir = outputJar.parentFile.resolve("log")
    renameLogDir.mkdirs()
    val renameLog = renameLogDir.resolve("${outputJar.name}.yguard-map.xml")
    val keptScreenClasses = ZipFile(inputJar).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.startsWith("com/cozooo/dlc_manager/client/screen/") && it.name.endsWith(".class") }
            .map { it.name.removeSuffix(".class").replace('/', '.') }
            .toList()
    }

    ant.withGroovyBuilder {
        "taskdef"(
            "name" to "yguard",
            "classname" to "com.yworks.yguard.YGuardTask",
            "classpath" to configurations.getByName("yguardTool").asPath,
        )
        "yguard" {
            "inoutpair"("in" to inputJar.absolutePath, "out" to outputJar.absolutePath)
            "externalclasses" {
                "fileset"("dir" to libDir.absolutePath) {
                    "include"("name" to "*.jar")
                }
            }
            "rename"("logfile" to renameLog.absolutePath, "replaceClassNameStrings" to true) {
                "adjust"("replaceContent" to true, "replaceContentSeparator" to ".", "replaceName" to false) {
                    "include"("name" to "fabric.mod.json")
                    "include"("name" to "*.mixins.json")
                    "include"("name" to "**/*.mixins.json")
                }
                "keep" {
                    keptScreenClasses.forEach { className ->
                        "class"("name" to className, "methods" to "private", "fields" to "private")
                    }
                    "class"("methods" to "private", "fields" to "private")
                }
            }
        }
    }
}

repositories {
    mavenCentral()
}

if (!rootProject.extensions.extraProperties.has("dlcRootBuildTasksConfigured")) {
    rootProject.extensions.extraProperties.set("dlcRootBuildTasksConfigured", true)
    rootProject.run {
    configurations.maybeCreate("yguardTool")

    dependencies {
        "yguardTool"("com.yworks:yguard:4.1.1")
    }

    tasks.withType<GradleBuild>().configureEach {
        startParameter.projectProperties.putAll(gradle.startParameter.projectProperties)
    }

    tasks.register("stoneCheck") {
        group = "verification"
        description = "Run checks through Stonecutter/StoneCraft version nodes."
        dependsOn(stoneNodes.map { ":$it:check" })
    }

    tasks.register("stoneTest") {
        group = "verification"
        description = "Run tests through Stonecutter/StoneCraft version nodes."
        dependsOn(stoneNodes.map { ":$it:test" })
    }

    tasks.register("stoneBuild") {
        group = "build"
        description = "Build remapped jars through Stonecutter/StoneCraft version nodes."
        dependsOn(stoneNodes.map { ":$it:remapJar" })
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(tasks.named("stoneCheck"))
    }

    tasks.matching { it.name == "build" }.configureEach {
        dependsOn(tasks.named("stoneBuild"))
    }

    if (tasks.findByName("test") == null) {
        tasks.register("test") {
            group = "verification"
            description = "Run all tests."
            dependsOn(tasks.named("stoneTest"))
        }
    } else {
        tasks.named("test") {
            dependsOn(tasks.named("stoneTest"))
        }
    }

    tasks.register("exportObfClasspathAll") {
        group = "build"
        description = "Export obfuscation classpath jars for all targets."
        dependsOn(stoneNodes.map { ":$it:exportObfClasspath" })
    }

    tasks.register("obfuscateJarYguard") {
        group = "build"
        description = "Obfuscate one jar with yGuard. Use -PobfInput=... -PobfOutput=... -PobfLibDir=..."
        doLast {
            val inputValue = findProperty("obfInput")?.toString()
            val outputValue = findProperty("obfOutput")?.toString()
            val libValue = findProperty("obfLibDir")?.toString()
            require(!inputValue.isNullOrBlank() && !outputValue.isNullOrBlank() && !libValue.isNullOrBlank()) {
                "Missing required properties: -PobfInput=... -PobfOutput=... -PobfLibDir=..."
            }
            runYGuardObfuscation(file(inputValue), file(outputValue), file(libValue))
        }
    }

    fun registerObfuscationTask(taskName: String, nodeName: String) {
        val loaderName = nodeName.substringAfterLast("-")
        val minecraftVersion = nodeName.substringBeforeLast("-")
        val modVersion = prop("mod_version")
        tasks.register(taskName) {
            group = "build"
            description = "Obfuscate $loaderName $minecraftVersion output jar with yGuard."
            dependsOn(":$nodeName:exportObfClasspath", tasks.named("stoneBuild"))
            doLast {
                runYGuardObfuscation(
                    file("output/$modVersion/DLC-Manager-$loaderName-$minecraftVersion-$modVersion.jar"),
                    file("output/$modVersion/yGuard/DLC-Manager-$loaderName-$minecraftVersion-$modVersion.jar"),
                    file("obf-lib/$loaderName-$minecraftVersion"),
                )
            }
        }
    }

    registerObfuscationTask("obfuscateFabric1201Yguard", "1.20.1-fabric")
    registerObfuscationTask("obfuscateForge1201Yguard", "1.20.1-forge")
    registerObfuscationTask("obfuscateFabric1211Yguard", "1.21.1-fabric")
    registerObfuscationTask("obfuscateNeoForge1211Yguard", "1.21.1-neoforge")

    tasks.register("obfuscateAllYguard") {
        group = "build"
        description = "Obfuscate all target jars with yGuard."
        dependsOn(
            "obfuscateFabric1201Yguard",
            "obfuscateForge1201Yguard",
            "obfuscateFabric1211Yguard",
            "obfuscateNeoForge1211Yguard",
        )
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
        dir = file("helper/file-ops-helper")
        tasks = listOf("build")
    }
    tasks.register<GradleBuild>("helperCheck") {
        group = "verification"
        description = "Compile-check helper library."
        dir = file("helper/file-ops-helper")
        tasks = listOf("check")
    }
    tasks.register<GradleBuild>("helperClean") {
        group = "build"
        description = "Clean helper library outputs."
        dir = file("helper/file-ops-helper")
        tasks = listOf("clean")
    }
    tasks.register<GradleBuild>("helperPublish") {
        group = "publishing"
        description = "Publish helper library to configured Maven repository."
        dir = file("helper/file-ops-helper")
        tasks = listOf("publish")
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

    tasks.register("publishMaven") {
        group = "publishing"
        description = "Publish all mod artifacts to Cozooo Nexus."
        dependsOn(stoneNodes.map { ":$it:publishMavenJavaPublicationToCozoooNexusRepository" })
    }
    }
}

if (stoneNodes.contains(project.name)) {
    val nodeName = project.name
    val loaderName = nodeName.substringAfterLast("-")
    val minecraftVersion = nodeName.substringBeforeLast("-")
    val kotlinEnabled = booleanProp("enable_kotlin", true)
    val kotlinExcludeJavaCompile = kotlinEnabled && booleanProp("kotlin_exclude_java_compile", false)
    val javaVersion = prop("java_version").toInt()

    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir(nodeName))

    loadProperties(rootProject.file("versions/$nodeName.properties")).forEach { (key, value) ->
        extensions.extraProperties[key] = value
    }

    group = prop("mod_group")
    version = prop("mod_version")

    repositories {
        maven {
            name = "Minecraft Libraries"
            url = uri("https://libraries.minecraft.net")
            content {
                includeGroup("org.lwjgl")
            }
        }
        maven("https://maven.cozozjw.cn/repository/maven-public/")
        maven {
            name = "ParchmentMC"
            url = uri("https://maven.parchmentmc.org")
        }
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
        if (kotlinEnabled) {
            maven {
                name = "Kotlin for Forge"
                url = uri("https://thedarkcolour.github.io/KotlinForForge/")
            }
        }
        maven("https://api.modrinth.com/maven")
        maven("https://maven.terraformersmc.com")
        maven("https://cursemaven.com")
        mavenCentral()
    }

    if (kotlinEnabled) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(javaVersion)
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(javaVersion)
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    tasks.withType<Jar>().configureEach {
        exclude("rebel.xml")
    }

    val generatedMainDir = layout.buildDirectory.dir("generated/dlc/main")
    val generatedTestDir = layout.buildDirectory.dir("generated/dlc/test")
    val commonMainJava = rootProject.file("common/src/main/java")
    val commonMainKotlin = rootProject.file("common/src/main/kotlin")
    val commonMainResources = rootProject.file("common/src/main/resources")
    val commonPlatformJava = rootProject.file("common/src/platform/java")
    val commonPlatformKotlin = rootProject.file("common/src/platform/kotlin")
    val commonPlatformResources = rootProject.file("common/src/platform/resources")
    val commonTestJava = rootProject.file("common/src/test/java")
    val commonTestResources = rootProject.file("common/src/test/resources")

    val generateDlcSources = tasks.register("generateDlcSources") {
        listOf(
            commonMainJava,
            commonMainKotlin,
            commonMainResources,
            commonPlatformJava,
            commonPlatformKotlin,
            commonPlatformResources,
        ).filter(File::exists).forEach(inputs::dir)
        inputs.property("dlcLoaderName", loaderName)
        inputs.property("dlcMinecraftVersion", minecraftVersion)
        outputs.dir(generatedMainDir)
        doLast {
            val outputRoot = generatedMainDir.get().asFile
            delete(outputRoot)
            copyProcessedTree(commonMainJava, outputRoot.resolve("java"), loaderName, minecraftVersion)
            copyProcessedTree(commonPlatformJava, outputRoot.resolve("java"), loaderName, minecraftVersion)
            copyProcessedTree(commonMainKotlin, outputRoot.resolve("kotlin"), loaderName, minecraftVersion)
            copyProcessedTree(commonPlatformKotlin, outputRoot.resolve("kotlin"), loaderName, minecraftVersion)
            copyProcessedTree(commonMainResources, outputRoot.resolve("resources"), loaderName, minecraftVersion)
            copyProcessedTree(commonPlatformResources, outputRoot.resolve("resources"), loaderName, minecraftVersion)
        }
    }

    extensions.configure<SourceSetContainer> {
        named("main") {
            java.setSrcDirs(if (kotlinExcludeJavaCompile) emptyList<File>() else listOf(generatedMainDir.map { it.dir("java") }))
            resources.setSrcDirs(listOf(generatedMainDir.map { it.dir("resources") }))
        }
        named("test") {
            java.setSrcDirs(listOf(commonTestJava))
            resources.setSrcDirs(listOf(commonTestResources, generatedTestDir.map { it.dir("resources") }))
        }
    }

    if (kotlinEnabled) {
        extensions.configure<KotlinJvmProjectExtension> {
            sourceSets.named("main") {
                kotlin.setSrcDirs(listOf(generatedMainDir.map { it.dir("kotlin") }))
            }
        }
    }

    tasks.matching {
        it.name in setOf("compileJava", "compileKotlin", "processResources", "jar", "remapJar")
    }.configureEach {
        dependsOn(generateDlcSources)
    }

    if (kotlinExcludeJavaCompile) {
        tasks.named<JavaCompile>("compileJava") {
            enabled = false
            setDependsOn(emptyList<Any>())
        }
        tasks.named<Jar>("jar") {
            doFirst {
                clearMainJavaClasses()
            }
        }
    } else if (!kotlinEnabled) {
        tasks.named<JavaCompile>("compileJava") {
            doFirst {
                deleteKotlinClassesReplacedByJava(
                    listOf(commonMainKotlin, commonPlatformKotlin),
                    layout.buildDirectory.dir("classes/kotlin/main").get().asFile,
                )
            }
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
                "mafglib_version_range" to optionalProp("mafglib_version_range").orEmpty(),
                "malilib_version" to prop("malilib_version"),
                "pack_format" to prop("pack_format"),
            )
        )
    }

    extensions.configure<LoomGradleExtensionAPI> {
        runs {
            named("client") {
                runDirectory.set(rootProject.layout.projectDirectory.dir("run/$loaderName/$minecraftVersion/client"))
            }
            named("server") {
                runDirectory.set(rootProject.layout.projectDirectory.dir("run/$loaderName/$minecraftVersion/server"))
            }
        }
    }

    configurations {
        maybeCreate("embedded")
        if (loaderName == "fabric") {
            maybeCreate("obfLib")
        }
        if ((loaderName == "forge" || loaderName == "neoforge") && kotlinEnabled) {
            maybeCreate("kotlinForForgeModDiscovery")
        }
        if (loaderName == "neoforge") {
            named("testRuntimeClasspath") {
                exclude(group = "net.neoforged", module = "neoforge")
            }
        }
    }

    fun addEmbedded(notation: String) {
        dependencies.add("implementation", notation)
        dependencies.add("include", notation)
    }

    dependencies {
        val architecturyDependency = when (loaderName) {
            "fabric" -> "dev.architectury:architectury-fabric:${prop("architectury_api_version")}"
            "forge" -> "dev.architectury:architectury-forge:${prop("architectury_api_version")}"
            "neoforge" -> "dev.architectury:architectury-neoforge:${prop("architectury_api_version")}"
            else -> error("Unsupported loader: $loaderName")
        }
        add("modImplementation", architecturyDependency)

        if (loaderName == "fabric") {
            add("modImplementation", "maven.modrinth:malilib:${prop("malilib_version")}")
            if (!optionalProp("modmenu_version").isNullOrBlank()) {
                add("modCompileOnly", "com.terraformersmc:modmenu:${prop("modmenu_version")}")
            }
            if (kotlinEnabled) {
                add("modImplementation", "net.fabricmc:fabric-language-kotlin:${prop("fabric_language_kotlin_version")}")
            }
            add("obfLib", "net.fabricmc:intermediary:${prop("minecraft_version")}:v2")
        }

        if (loaderName == "forge") {
            add("compileOnly", "curse.maven:mafglib-910766:${prop("mafglib_curse_file")}")
            if (kotlinEnabled) {
                add("modImplementation", "thedarkcolour:kotlinforforge:${prop("kotlin_for_forge_version")}")
                add("kotlinForForgeModDiscovery", "thedarkcolour:kotlinforforge:${prop("kotlin_for_forge_version")}:all") {
                    isTransitive = false
                }
            }
            add("modRuntimeOnly", "curse.maven:mafglib-910766:${prop("mafglib_curse_file")}")
        }

        if (loaderName == "neoforge") {
            add("compileOnly", "curse.maven:mafglib-910766:${prop("mafglib_curse_file")}")
            if (kotlinEnabled) {
                add("kotlinForForgeModDiscovery", "thedarkcolour:kotlinforforge-neoforge:${prop("kotlin_for_forge_version")}") {
                    isTransitive = false
                }
                add("modImplementation", "thedarkcolour:kfflang-neoforge:${prop("kotlin_for_forge_version")}") {
                    isTransitive = false
                }
                add("modImplementation", "thedarkcolour:kffmod-neoforge:${prop("kotlin_for_forge_version")}") {
                    isTransitive = false
                }
            }
            add("modRuntimeOnly", "curse.maven:mafglib-910766:${prop("mafglib_curse_file")}")
        }

        addEmbedded("com.cozooo.dlc_manager:dlc-fileops-helper:${prop("file_ops_helper_version")}")
        addEmbedded("org.commonmark:commonmark:0.21.0")
        addEmbedded("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")

        if (loaderName == "forge" || loaderName == "neoforge") {
            add("forgeRuntimeLibrary", "com.cozooo.dlc_manager:dlc-fileops-helper:${prop("file_ops_helper_version")}")
            add("forgeRuntimeLibrary", "org.commonmark:commonmark:0.21.0")
            add("forgeRuntimeLibrary", "org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
        }

        add("testImplementation", "org.junit.jupiter:junit-jupiter:${rootProject.prop("junit_jupiter_version")}")
        add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:${rootProject.prop("junit_jupiter_version")}")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    if ((loaderName == "forge" || loaderName == "neoforge") && kotlinEnabled) {
        val discoveryJarName = if (loaderName == "forge") "kotlinforforge-all.jar" else "kotlinforforge-neoforge.jar"
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
            mods {
                maybeCreate("dlc_manager").sourceSet(extensions.getByType<SourceSetContainer>().named("main").get())
                maybeCreate("kotlinforforge").modFiles.from(files(discoveryJar).builtBy(prepareKotlinForForgeModDiscovery))
            }
        }
    }

    tasks.named("processResources") {
        doLast {
            if (loaderName == "fabric" && kotlinEnabled) {
                val outputFile = layout.buildDirectory.file("resources/main/fabric.mod.json").get().asFile
                if (outputFile.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val json = JsonSlurper().parse(outputFile) as MutableMap<String, Any?>
                    @Suppress("UNCHECKED_CAST")
                    val depends = json["depends"] as MutableMap<String, Any?>
                    depends["fabric-language-kotlin"] = ">=${prop("fabric_language_kotlin_version")}"
                    outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n")
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

    tasks.named<RemapJarTask>("remapJar") {
        archiveFileName.set("DLC-Manager-$loaderName-$minecraftVersion-${rootProject.prop("mod_version")}.jar")
        destinationDirectory.set(rootProject.layout.projectDirectory.dir("output/${rootProject.prop("mod_version")}"))
    }

    tasks.register<Sync>("exportObfClasspath") {
        group = "build"
        description = "Export obfuscation classpath jars for $loaderName $minecraftVersion."
        from({
            val jars = linkedSetOf<File>()
            listOf(configurations.getByName("compileClasspath"), configurations.getByName("runtimeClasspath")).forEach { configuration ->
                jars += configuration.resolve().filter { it.name.endsWith(".jar") }
            }
            if (loaderName == "fabric") {
                jars += configurations.getByName("obfLib").resolve().filter { it.name.endsWith(".jar") }
            }
            jars
        })
        into(rootProject.layout.projectDirectory.dir("obf-lib/$loaderName-$minecraftVersion"))
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
        repositories {
            val mavenUsername = findProperty("mavenUsername")?.toString() ?: System.getenv("MAVEN_USERNAME")
            val mavenPassword = findProperty("mavenPassword")?.toString() ?: System.getenv("MAVEN_PASSWORD")
            if (!mavenUsername.isNullOrBlank() && !mavenPassword.isNullOrBlank()) {
                maven {
                    name = "CozoooNexus"
                    url = uri("https://maven.cozozjw.cn/repository/maven-releases/")
                    credentials {
                        username = mavenUsername
                        password = mavenPassword
                    }
                }
            }
        }
    }
}
