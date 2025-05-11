rootProject.name = "gimlee-backend"

// Include all modules
include("gimlee-common")
include("gimlee-events")
include("gimlee-notifications")
include("gimlee-auth")
include("gimlee-media-store")
include("gimlee-payments")
include("gimlee-ads")
include("gimlee-api")

// Configure project directories
project(":gimlee-common").projectDir = file("gimlee-common")
project(":gimlee-events").projectDir = file("gimlee-events")
project(":gimlee-auth").projectDir = file("gimlee-auth")
project(":gimlee-media-store").projectDir = file("gimlee-media-store")
project(":gimlee-payments").projectDir = file("gimlee-payments")
project(":gimlee-ads").projectDir = file("gimlee-ads")
project(":gimlee-api").projectDir = file("gimlee-api")
project(":gimlee-notifications").projectDir = file("gimlee-notifications")
