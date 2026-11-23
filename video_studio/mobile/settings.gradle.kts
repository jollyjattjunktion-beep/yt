pluginManagement {
    repositories {
        google()
        // Google-hosted Maven Central mirror helps avoid transient Central HTTP 429 throttling.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Prefer this mirror before Maven Central for Android dependency resolution.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
    }
}
rootProject.name = "PreetSportsStudio"
include(":app")
