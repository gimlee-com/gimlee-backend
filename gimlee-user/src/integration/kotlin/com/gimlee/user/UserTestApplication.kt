package com.gimlee.user

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.gimlee.user", "com.gimlee.common.config"])
class UserTestApplication

fun main(args: Array<String>) {
    runApplication<UserTestApplication>(*args)
}
