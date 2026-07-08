plugins {
    `kotlin-dsl`
}

layout.buildDirectory.set(file("../build/buildSrc"))

repositories {
    gradlePluginPortal()
    mavenCentral()
}
