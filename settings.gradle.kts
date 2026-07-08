pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.meza.gg/releases")
        maven("https://maven.meza.gg/snapshots")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net")
        maven("https://maven.cozozjw.cn/repository/maven-public/")
    }
    plugins {
        id("gg.meza.stonecraft") version "1.12.4"
        id("dev.kikugie.stonecutter") version "0.9.6"
        id("com.gradleup.shadow") version "8.3.11"
        id("org.jetbrains.kotlin.jvm") version "2.2.20"
        id("me.modmuss50.mod-publish-plugin") version "2.1.1"
    }
}

plugins {
    id("gg.meza.stonecraft")
    id("dev.kikugie.stonecutter")
}

rootProject.name = "DLC-Manager"

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true

    shared {
        fun mc(version: String, vararg loaders: String) {
            loaders.forEach { loader ->
                version("$version-$loader", version)
            }
        }

        mc("1.20.1", "fabric", "forge")
        mc("1.21.1", "fabric", "neoforge")

        vcsVersion = "1.21.1-fabric"
    }

    create(rootProject)
}

includeBuild("tools/dlc-javafx-manager") {
    name = "dlcJavafxManager"
}
