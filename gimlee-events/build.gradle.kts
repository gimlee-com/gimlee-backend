dependencies {
    // Common Kotlin dependencies
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    // Other dependencies
    implementation(libs.bson)

    // Common Test dependencies
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
}

// Configure source sets
sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin")
    }
    test {
        kotlin.srcDirs("src/test/kotlin")
    }
}

// Disable the bootJar task since this is a library module
tasks.named("bootJar") {
    enabled = false
}

// Enable the jar task
tasks.named("jar") {
    enabled = true
}