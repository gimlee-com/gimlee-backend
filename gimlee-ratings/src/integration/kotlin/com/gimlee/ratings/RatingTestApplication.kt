package com.gimlee.ratings

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication(scanBasePackages = ["com.gimlee.ratings", "com.gimlee.auth", "com.gimlee.common.config"])
@Import(RatingTestConfig::class)
class RatingTestApplication

fun main(args: Array<String>) {
    runApplication<RatingTestApplication>(*args)
}
