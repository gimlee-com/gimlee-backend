package com.gimlee.api

import com.gimlee.common.BaseIntegrationTest

class GimleeApiApplicationTests : BaseIntegrationTest({

	Given("context loads") {
		Then("context should load") {
			println(environment.activeProfiles)
			assert(true)
		}
	}
})
