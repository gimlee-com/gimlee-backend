package com.gimlee.location

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication(scanBasePackages = ["com.gimlee.location", "com.gimlee.common.config"])
@Import(LocationTestConfig::class)
class LocationTestApplication

fun main(args: Array<String>) {
    runApplication<LocationTestApplication>(*args)
}
