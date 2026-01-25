package com.gimlee.ads

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Import
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.gimlee.ads", "com.gimlee.common"])
@Import(AdTestConfig::class)
class TestApplication

fun main(args: Array<String>) {
    runApplication<TestApplication>(*args)
}

