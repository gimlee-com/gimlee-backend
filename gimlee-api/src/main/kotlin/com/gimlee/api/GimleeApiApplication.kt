package com.gimlee.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.web.config.EnableSpringDataWebSupport
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO

@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
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
	"com.gimlee.chat",
	"com.gimlee.analytics",
	"com.gimlee.api",
])
class GimleeApiApplication

fun main(args: Array<String>) {
	runApplication<GimleeApiApplication>(*args)
}
