pluginManagement {
    val ciOfficial = System.getenv("CI") == "true" ||
        providers.gradleProperty("useOfficialRepos").orNull == "true" ||
        System.getenv("USE_OFFICIAL_REPOS") == "true"
    repositories {
        if (ciOfficial) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val ciOfficial = System.getenv("CI") == "true" ||
        providers.gradleProperty("useOfficialRepos").orNull == "true" ||
        System.getenv("USE_OFFICIAL_REPOS") == "true"
    repositories {
        if (ciOfficial) {
            google()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            google()
            mavenCentral()
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-logic")

rootProject.name = "TaiXu"

include(":app")
include(":baselineprofile")

// Core modules
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:security")
include(":core:browser")

// Feature modules
include(":feature:components")
include(":feature:theme")
include(":feature:home")
include(":feature:chat")
include(":feature:terminal")
include(":feature:workflow")
include(":feature:settings")
include(":feature:developer")
include(":feature:custom_iteration")
include(":feature:onboarding")
include(":feature:navigation")
include(":feature:browser")
include(":feature:git")
include(":feature:bot")
include(":feature:a2uipoc")
