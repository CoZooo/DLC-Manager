pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.cozozjw.cn/repository/maven-public/")
        mavenCentral()
    }
}

rootProject.name = "dlc-javafx-manager"
