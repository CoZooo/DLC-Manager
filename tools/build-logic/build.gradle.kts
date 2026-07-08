plugins {
    `kotlin-dsl`
}

group = "com.cozooo.dlc_manager.gradle"
version = "1.0.0"

layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("../../build/build-logic"))

repositories {
    gradlePluginPortal()
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("dlcBuildLogic") {
            id = "com.cozooo.dlc-manager.build-logic"
            implementationClass = "com.cozooo.dlc_manager.gradle.DlcBuildLogicPlugin"
        }
    }
}
