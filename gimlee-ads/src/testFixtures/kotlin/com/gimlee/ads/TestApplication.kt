package com.gimlee.ads

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.gimlee.ads", "com.gimlee.common"])
class TestApplication

fun main(args: Array<String>) {
    runApplication<TestApplication>(*args)
}

