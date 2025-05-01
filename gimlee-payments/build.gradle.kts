dependencies {
    // Module dependencies
    implementation(project(":gimlee-common"))
    implementation(project(":gimlee-events"))
    implementation(project(":gimlee-auth"))
    
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