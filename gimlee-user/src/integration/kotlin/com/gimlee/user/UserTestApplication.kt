package com.gimlee.user

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication(scanBasePackages = ["com.gimlee.user", "com.gimlee.auth", "com.gimlee.common.config"])
@Import(UserTestConfig::class)
class UserTestApplication

fun main(args: Array<String>) {
    runApplication<UserTestApplication>(*args)
}
