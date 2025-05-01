dependencies {
    // Common Kotlin dependencies
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    // Dependencies
    implementation(libs.spring.boot.starter.web)
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