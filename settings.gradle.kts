rootProject.name = "reservation"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    ":contracts",
    ":common-infrastructure",
    ":hotel-service",
    ":rate-service",
    ":guest-service",
    ":reservation-service",
)
