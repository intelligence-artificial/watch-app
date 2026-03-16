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

rootProject.name = "PixelFaceWatch"
include(":wear")
// watch_face module disabled — redundant; wear already has PixelFaceWatchFaceService
// include(":watch_face")
include(":mobile")
