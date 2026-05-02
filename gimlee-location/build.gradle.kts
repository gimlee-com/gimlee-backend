dependencies {
    // Module dependencies
    implementation(project(":gimlee-common"))

    // Spring Boot dependencies
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.data.mongodb)

    // Search
    implementation(libs.lucene.core)
    implementation(libs.lucene.queryparser)
    implementation(libs.lucene.analysis.common)

    // Scheduling & locks
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.mongo)

    // Other dependencies
    implementation(libs.caffeine)
    implementation(libs.jakarta.validation)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    implementation(libs.maxmind.geoip2)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.mongodb)
    integrationImplementation(testFixtures(project(":gimlee-common")))
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