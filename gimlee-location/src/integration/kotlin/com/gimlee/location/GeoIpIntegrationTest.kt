package com.gimlee.location

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.web.dto.StatusResponseDto
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe

class GeoIpIntegrationTest : BaseIntegrationTest({

    Given("the GeoIP endpoint") {

        When("requesting country detection with a known US IP via X-Forwarded-For") {
            val response = restClient.get(
                "/location/geoip/country",
                headers = mapOf("X-Forwarded-For" to "8.8.8.8")
            )

            Then("it should return the US country code") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<StatusResponseDto>()!!
                body.success shouldBe true
                body.status shouldBe "LOCATION_GEOIP_COUNTRY_DETECTED"
                @Suppress("UNCHECKED_CAST")
                val data = body.data as Map<String, Any>
                data shouldContainKey "countryCode"
                data["countryCode"] shouldBe "US"
            }
        }

        When("requesting country detection with a known Polish IP via X-Forwarded-For") {
            val response = restClient.get(
                "/location/geoip/country",
                headers = mapOf("X-Forwarded-For" to "212.77.100.101")
            )

            Then("it should return the PL country code") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<StatusResponseDto>()!!
                body.success shouldBe true
                body.status shouldBe "LOCATION_GEOIP_COUNTRY_DETECTED"
                @Suppress("UNCHECKED_CAST")
                val data = body.data as Map<String, Any>
                data["countryCode"] shouldBe "PL"
            }
        }

        When("requesting country detection with a localhost IP via X-Forwarded-For") {
            val response = restClient.get(
                "/location/geoip/country",
                headers = mapOf("X-Forwarded-For" to "127.0.0.1")
            )

            Then("it should return 404 with country unknown status") {
                response.statusCode shouldBe 404
                val body = response.bodyAs<StatusResponseDto>()!!
                body.success shouldBe false
                body.status shouldBe "LOCATION_GEOIP_COUNTRY_UNKNOWN"
            }
        }
    }
})
