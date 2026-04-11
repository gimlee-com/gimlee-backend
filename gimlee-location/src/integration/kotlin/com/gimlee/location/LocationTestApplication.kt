package com.gimlee.location

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.gimlee.location", "com.gimlee.common.config"])
class LocationTestApplication

fun main(args: Array<String>) {
    runApplication<LocationTestApplication>(*args)
}
