pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
rootProject.name = "dclanguage-lsp"
includeBuild("..") {
    dependencySubstitution { substitute(module("com.cozooo:dclanguage")).using(project(":")) }
}
