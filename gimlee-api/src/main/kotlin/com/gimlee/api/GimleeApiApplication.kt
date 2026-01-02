package com.gimlee.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = [
	"com.gimlee.auth",
	"com.gimlee.notifications",
	"com.gimlee.mediastore",
	"com.gimlee.ads",
	"com.gimlee.payments",
	"com.gimlee.location",
	"com.gimlee.purchases",
	"com.gimlee.user",
	"com.gimlee.common",
	"com.gimlee.api",
])
class GimleeApiApplication

fun main(args: Array<String>) {
	runApplication<GimleeApiApplication>(*args)
}
