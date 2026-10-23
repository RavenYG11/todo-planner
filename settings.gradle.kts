pluginManagement {
    repositories {
        // Repo lokal (hanya ada di VM build; di laptop user folder ini tidak ada -> repo online standar).
        if (java.io.File(rootDir, "local-repo").exists()) {
            maven { url = uri("local-repo") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (java.io.File(rootDir, "local-repo").exists()) {
            maven { url = uri("local-repo") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "TodoPlanner"
include(":app")
