plugins {
    `java-library`
}

dependencies {
    // Common Kotlin dependencies
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    
    // Dependencies
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.jakarta.validation)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.mongo)
    api(libs.xxhash)
    
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(platform(libs.kotest.bom))
    testFixturesApi(libs.kotest.runner.junit5)
    testFixturesApi(libs.kotest.assertions.core)
    testFixturesApi(libs.kotest.property)
    testFixturesApi(libs.kotest.extensions.spring)
    
    testFixturesApi(platform(libs.testcontainers.bom))
    testFixturesApi(libs.testcontainers.mongodb)
    testFixturesApi(libs.wiremock.standalone)
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