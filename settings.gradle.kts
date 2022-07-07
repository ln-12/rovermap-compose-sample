pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()

        flatDir {
            dirs("libs/")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "rovermap-compose-sample"
include(":app")
