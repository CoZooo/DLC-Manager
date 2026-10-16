pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net")
    }
    val managerProperties = java.util.Properties().apply {
        file("gradle.properties").inputStream().use(::load)
    }
    plugins {
        id("gg.meza.stonecraft") version requireNotNull(managerProperties.getProperty("stonecraft_version"))
        id("dev.kikugie.stonecutter") version requireNotNull(managerProperties.getProperty("stonecutter_version"))
        id("dev.architectury.loom") version "1.17-20260912.095804-1"
        id("com.gradleup.shadow") version "8.3.11"
        id("org.jetbrains.kotlin.jvm") version requireNotNull(managerProperties.getProperty("kotlin_version"))
    }
}

plugins {
    id("gg.meza.stonecraft")
    id("dev.kikugie.stonecutter")
}

rootProject.name = "DLC-Manager"

fun discoverActiveStoneNode(defaultNode: String): String {
    val activeFile = file("stonecutter.gradle.kts")
    if (!activeFile.isFile) {
        return defaultNode
    }
    return Regex("stonecutter\\s+active\\s+\"([^\"]+)\"")
        .find(activeFile.readText())
        ?.groupValues
        ?.get(1)
        ?: defaultNode
}

fun isRootRunTaskRequest(): Boolean =
    gradle.startParameter.taskNames.any { taskName ->
        taskName.removePrefix(":") in setOf("runActive", "runActiveServer")
    }

fun requestedStoneNodes(allVersionNodes: List<String>): List<String> {
    val requestedNodes = gradle.startParameter.taskNames.mapNotNull { taskName ->
        val normalized = taskName.removePrefix(":")
        val firstSegment = normalized.substringBefore(":")
        firstSegment.takeIf(allVersionNodes::contains)
    }.distinct()
    return requestedNodes
}

gradle.beforeProject {
    if (Regex("""\d+\.\d+(?:\.\d+)?-.+""").matches(name)) {
        val javaOnly = providers.gradleProperty("enable_kotlin")
            .map { it.toBooleanStrictOrNull() == false }
            .getOrElse(false)
        val nodeBuildDir = rootProject.layout.buildDirectory.dir(if (javaOnly) "java-only/$name" else name)
        layout.buildDirectory.set(nodeBuildDir)
    }
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true

    shared {
        val allVersionNodes = file("versions")
            .listFiles { file -> file.isDirectory && file.resolve("gradle.properties").isFile }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        val vcsNode = "1.20.1-forge"
        val activeNode = discoverActiveStoneNode(vcsNode)
        val requestedNodes = requestedStoneNodes(allVersionNodes)
        val versionNodes = when {
            requestedNodes.isNotEmpty() -> (requestedNodes + activeNode).distinct().filter(allVersionNodes::contains)
            isRootRunTaskRequest() -> listOf(activeNode).filter(allVersionNodes::contains)
            else -> allVersionNodes
        }

        versionNodes.forEach { node ->
            version(node, node.substringBeforeLast('-'))
        }

        vcsVersion = versionNodes.firstOrNull { it == activeNode }
            ?: versionNodes.firstOrNull()
            ?: activeNode
    }

    create(rootProject)
}

includeBuild("tools/dlc-javafx-manager") {
    name = "dlcJavafxManager"
}

includeBuild("tools/dlc-bootstrap") {
    name = "dlcBootstrap"
}
