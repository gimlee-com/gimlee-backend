import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    `jvm-test-suite`
    `java-base`
}

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jvm-test-suite")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    configure<TestingExtension> {
        suites {
            register("integration", JvmTestSuite::class) {
                sources {
                    kotlin.srcDirs("src/integration/kotlin")
                    resources.srcDirs("src/main/resources", "src/integration/resources")
                }

                dependencies {
                    implementation(project())
                }

                targets {
                    all {
                        testTask.configure {
                            shouldRunAfter(tasks.test)
                            classpath += sourceSets.main.get().output
                        }
                    }
                }

            }
        }
    }

    configurations {
        named("integrationImplementation") {
            extendsFrom(getByName("testImplementation"))
        }
        named("integrationRuntimeOnly") {
            extendsFrom(getByName("testRuntimeOnly"))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        if (name == "integration") {
            description = "Runs integration tests."
            group = "verification"
            systemProperty("spring.profiles.active", "integration")
        } else if (name == "test") {
            description = "Runs unit tests."
            group = "verification"
        }
    }

    tasks.named("check") {
        dependsOn("integration")
    }
}

tasks.named("bootJar") {
    enabled = false
}
