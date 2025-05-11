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

    // Other dependencies
    implementation(libs.simmetrics.core)
    implementation(libs.caffeine)
    implementation(libs.jakarta.validation)
    implementation(libs.jackson.dataformat.csv)
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