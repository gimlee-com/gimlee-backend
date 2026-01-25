dependencies {
    // Module dependencies
    implementation(project(":gimlee-common"))
    implementation(project(":gimlee-events"))
    implementation(project(":gimlee-auth"))
    implementation(project(":gimlee-location"))
    implementation(project(":gimlee-payments"))

    // Spring Boot dependencies
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.data.mongodb)

    // Other dependencies
    implementation(libs.httpclient5)
    implementation(libs.caffeine)
    implementation(libs.commons.codec)
    implementation(libs.jakarta.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.mongo)
    
    testFixturesApi(libs.spring.boot.starter)
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.shedlock.spring)
    testFixturesApi(libs.shedlock.provider.mongo)
    testFixturesImplementation(project(":gimlee-payments"))
    testFixturesImplementation(project(":gimlee-auth"))
    testFixturesImplementation(libs.mockk)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.mockk)
    testImplementation(testFixtures(project))
    testImplementation(testFixtures(project(":gimlee-common")))
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