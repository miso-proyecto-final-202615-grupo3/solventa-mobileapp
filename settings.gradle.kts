pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // SDK de Didit (HU3.1.1, solo demostración): restringido a su grupo.
        maven {
            url = uri("https://raw.githubusercontent.com/didit-protocol/sdk-android/main/repository")
            content { includeGroup("me.didit") }
        }
    }
}

rootProject.name = "solventa-mobileapp"
include(":app")
