dependencies {
    // Module dependencies
    implementation(project(":gimlee-common"))
    implementation(project(":gimlee-events"))

    // Spring Boot dependencies
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.data.mongodb)

    // Common Kotlin dependencies
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    // Dependencies
    implementation(libs.spring.boot.starter.web)
    implementation(libs.mustache.java)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    implementation(libs.jakarta.validation)
}

sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin")
        resources.srcDirs("src/main/resources")
    }
    test {
        kotlin.srcDirs("src/test/kotlin")
        resources.srcDirs("src/test/resources")
    }
}

tasks.named("bootJar") {
    enabled = false
}

tasks.named("jar") {
    enabled = true
}