package com.gimlee.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = [
	"com.gimlee.auth",
	"com.gimlee.mediastore",
	"com.gimlee.api",
	"com.gimlee.payments"
])
class GimleeApiApplication

fun main(args: Array<String>) {
	runApplication<GimleeApiApplication>(*args)
}
