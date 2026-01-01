package com.gimlee.api

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment

@SpringBootTest
class GimleeApiApplicationTests(
	private val environment: Environment
): BaseIntegrationTest({

	Given("context loads") {
		Then("context should load") {
			println(environment.activeProfiles)
			assert(true)
		}
	}
})
