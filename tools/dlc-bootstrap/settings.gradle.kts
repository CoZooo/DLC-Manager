pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
    val managerProperties = java.util.Properties().apply {
        file("../../gradle.properties").inputStream().use(::load)
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version requireNotNull(managerProperties.getProperty("kotlin_version"))
        id("dev.kikugie.stonecutter") version "0.10-alpha.12"
    }
}

plugins {
    id("dev.kikugie.stonecutter")
}

rootProject.name = "dlc-bootstrap"

val managerRoot = file("../..").canonicalFile
val managerStonecutterFile = managerRoot.resolve("stonecutter.gradle.kts")
val activeNode = Regex("stonecutter\\s+active\\s+\"([^\"]+)\"")
    .find(managerStonecutterFile.readText())
    ?.groupValues
    ?.get(1)
    ?: error("The main project does not declare an active StoneCutter node")

val bootstrapNodes = managerRoot.resolve("versions")
    .listFiles { file ->
        file.isDirectory &&
            file.resolve("gradle.properties").isFile &&
            (file.name.endsWith("-forge") || file.name.endsWith("-neoforge"))
    }
    ?.map { it.name to it.name.substringBeforeLast('-') }
    ?.sortedBy { it.first }
    ?: emptyList()

require(bootstrapNodes.isNotEmpty()) { "The main project does not declare Forge or NeoForge StoneCutter nodes" }

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true

    shared {
        bootstrapNodes.forEach { (node, minecraft) -> version(node, minecraft) }

        vcsVersion = activeNode.takeIf { bootstrapNodes.any { node -> node.first == it } }
            ?: bootstrapNodes.first().first
    }

    create(rootProject)
}

gradle.rootProject {
    val aggregateTargets = bootstrapNodes.groupBy { it.first.substringAfterLast('-') }
    tasks.register("test") {
        dependsOn(bootstrapNodes.map { ":${it.first}:test" })
    }
    aggregateTargets.forEach { (loader, targets) ->
        tasks.register("${loader}BootstrapJar") {
            dependsOn(targets.map { ":${it.first}:bootstrapJar" })
        }
    }
}
