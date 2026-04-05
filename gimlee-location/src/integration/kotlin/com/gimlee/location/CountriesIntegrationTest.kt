package com.gimlee.location

import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.collections.shouldBeMonotonicallyIncreasingWith
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

class CountriesIntegrationTest : BaseIntegrationTest({

    Given("the countries endpoint") {

        When("requesting the list of countries in English") {
            val response = restClient.get(
                "/location/countries/",
                headers = mapOf("Accept-Language" to "en-US")
            )

            Then("it should return 200 with a list of countries") {
                response.statusCode shouldBe 200
                val countries = response.bodyAs<List<Map<String, String>>>()!!
                countries.shouldNotBeEmpty()

                val poland = countries.find { it["code"] == "PL" }!!
                poland["name"] shouldBe "Poland"

                val us = countries.find { it["code"] == "US" }!!
                us["name"] shouldBe "United States"
            }

            Then("each country should have a code and a non-blank name") {
                val countries = response.bodyAs<List<Map<String, String>>>()!!
                countries.forEach { country ->
                    country["code"]!!.length shouldBe 2
                    country["name"]!!.shouldNotBeBlank()
                }
            }

            Then("the list should be sorted alphabetically by name") {
                val countries = response.bodyAs<List<Map<String, String>>>()!!
                val names = countries.map { it["name"]!! }
                names.shouldBeMonotonicallyIncreasingWith(compareBy { it })
            }
        }

        When("requesting the list of countries in Polish") {
            val response = restClient.get(
                "/location/countries/",
                headers = mapOf("Accept-Language" to "pl-PL")
            )

            Then("it should return Polish-localized country names") {
                response.statusCode shouldBe 200
                val countries = response.bodyAs<List<Map<String, String>>>()!!

                val poland = countries.find { it["code"] == "PL" }!!
                poland["name"] shouldBe "Polska"

                val us = countries.find { it["code"] == "US" }!!
                us["name"] shouldBe "Stany Zjednoczone"
            }

            Then("the Polish list should be sorted alphabetically by Polish name") {
                val countries = response.bodyAs<List<Map<String, String>>>()!!
                val names = countries.map { it["name"]!! }
                names.shouldBeMonotonicallyIncreasingWith(compareBy { it })
            }
        }
    }
})
