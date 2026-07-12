plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "KotlinCrud"

include(
    "proto:identity-proto",
    "proto:catalog-proto",
    "identity-service",
    "catalog-service",
    "order-service",
    "notification-service"
)
