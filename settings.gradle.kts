pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// This root project is now split into two independent sub-projects:
//   note-taking-app/  - Voice recorder (wear + mobile)
//   tamagotchi/        - Watch faces (WFF Tamagotchi + others)
// Each sub-project has its own settings.gradle.kts, build.sh, and install.sh.
rootProject.name = "WatchApps"
