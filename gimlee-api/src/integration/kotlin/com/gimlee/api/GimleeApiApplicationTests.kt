package com.gimlee.api

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringAutowireConstructorExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment

@SpringBootTest
class GimleeApiApplicationTests(
	private val environment: Environment
): BehaviorSpec({
	extensions(SpringAutowireConstructorExtension)

	Given("context loads") {
		Then("context should load") {
			println(environment.activeProfiles)
			assert(true)
		}
	}
})
