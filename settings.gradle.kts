pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "gimlee-backend"

// Include all modules
include("gimlee-common")
include("gimlee-events")
include("gimlee-notifications")
include("gimlee-auth")
include("gimlee-media-store")
include("gimlee-location")
include("gimlee-payments")
include("gimlee-ads")
include("gimlee-purchases")
include("gimlee-user")
include("gimlee-chat")
include("gimlee-analytics")
include("gimlee-api")

// Configure project directories
project(":gimlee-common").projectDir = file("gimlee-common")
project(":gimlee-events").projectDir = file("gimlee-events")
project(":gimlee-auth").projectDir = file("gimlee-auth")
project(":gimlee-media-store").projectDir = file("gimlee-media-store")
project(":gimlee-location").projectDir = file("gimlee-location")
project(":gimlee-payments").projectDir = file("gimlee-payments")
project(":gimlee-ads").projectDir = file("gimlee-ads")
project(":gimlee-purchases").projectDir = file("gimlee-purchases")
project(":gimlee-user").projectDir = file("gimlee-user")
project(":gimlee-chat").projectDir = file("gimlee-chat")
project(":gimlee-analytics").projectDir = file("gimlee-analytics")
project(":gimlee-api").projectDir = file("gimlee-api")
project(":gimlee-notifications").projectDir = file("gimlee-notifications")