import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.testing.Test

plugins {
    id("org.springframework.boot")
}

dependencies {
    // Module dependencies
    implementation(project(":gimlee-common"))
    implementation(project(":gimlee-auth"))
    implementation(project(":gimlee-events"))
    implementation(project(":gimlee-media-store"))
    implementation(project(":gimlee-payments"))
    implementation(project(":gimlee-ads"))
    implementation(project(":gimlee-location"))
    implementation(project(":gimlee-purchases"))
    implementation(project(":gimlee-user"))

    // Spring Boot dependencies
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.data.mongodb)

    // Other dependencies
    implementation(libs.aspectj.weaver)
    implementation(libs.auth0.java.jwt)
    implementation(libs.commons.lang3)
    implementation(libs.commons.io)
    implementation(libs.commons.codec)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.csv)
    implementation(libs.mustache.java)
    implementation(libs.micrometer.prometheus)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.caffeine)

    // Test dependencies
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.mockk)

    // Integration test dependencies
    integrationImplementation(libs.spring.boot.starter.test)
    integrationImplementation(platform(libs.testcontainers.bom))
    integrationImplementation(libs.testcontainers.mongodb)
    integrationImplementation(project(":gimlee-notifications"))
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
