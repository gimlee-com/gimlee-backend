package com.gimlee.location.geoip

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.model.CountryResponse
import com.maxmind.geoip2.record.Country
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.net.InetAddress

class GeoIpServiceTest : BehaviorSpec({

    Given("GeoIpService with no database reader") {
        val service = GeoIpService(null)

        Then("isAvailable should be false") {
            service.isAvailable shouldBe false
        }

        Then("resolveCountry should return null") {
            service.resolveCountry("8.8.8.8").shouldBeNull()
        }
    }

    Given("GeoIpService with a database reader") {
        val databaseReader = mockk<DatabaseReader>()
        val service = GeoIpService(databaseReader)

        Then("isAvailable should be true") {
            service.isAvailable shouldBe true
        }

        When("looking up a known IP address") {
            val country = mockk<Country>()
            every { country.isoCode() } returns "US"

            val countryResponse = mockk<CountryResponse>()
            every { countryResponse.country() } returns country
            every { databaseReader.country(InetAddress.getByName("8.8.8.8")) } returns countryResponse

            Then("it should return the country code") {
                service.resolveCountry("8.8.8.8") shouldBe "US"
            }
        }

        When("looking up an IP address not in the database") {
            every { databaseReader.country(InetAddress.getByName("127.0.0.1")) } throws
                    AddressNotFoundException("127.0.0.1 is not in the database.")

            Then("it should return null") {
                service.resolveCountry("127.0.0.1").shouldBeNull()
            }
        }

        When("looking up an invalid IP address") {
            Then("it should return null") {
                service.resolveCountry("not-an-ip").shouldBeNull()
            }
        }
    }
})
