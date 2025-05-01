dependencies {
    // Module dependencies
    implementation(project(":gimlee-common"))
    implementation(project(":gimlee-events"))

    // Spring Boot dependencies
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.data.mongodb)

    // Other dependencies
    implementation(libs.jakarta.activation.api) // <-- Corrected alias
    implementation(libs.commons.io)
    implementation(libs.thumbnailator)

    // --- Add the common dependencies moved from the root project ---
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    // --- End of common dependencies ---
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