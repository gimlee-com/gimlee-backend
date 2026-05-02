package com.gimlee.location.cities

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.location.cities.geonames.index.CitySearchIndex
import com.gimlee.location.cities.persistence.model.CityDocument
import com.gimlee.location.cities.persistence.model.CityNameDocument
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files

class CitiesIntegrationTest : BaseIntegrationTest({

    Given("the cities endpoint with seeded city data") {

        When("searching for 'War' without filters") {
            val response = restClient.get("/cities/suggestions?q=War")

            Then("it should return matching cities with region info") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                results.shouldNotBeEmpty()

                val warsaw = results.first { it["id"] == "756135" }
                warsaw["countryCode"] shouldBe "PL"
                (warsaw["population"] as Number).toLong() shouldBe 1_790_658L
                warsaw["region"] shouldBe "Masovian Voivodeship"
                warsaw["district"] shouldBe "Warsaw"
            }
        }

        When("searching for 'War' with country code PL") {
            val response = restClient.get("/cities/suggestions?q=War&cc=PL")

            Then("all results should be Polish cities") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                results.shouldNotBeEmpty()
                results.forEach { it["countryCode"] shouldBe "PL" }
            }
        }

        When("searching for 'War' with language en-US") {
            val response = restClient.get("/cities/suggestions?q=War&lang=en-US")

            Then("Warsaw should have its English name") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                val warsaw = results.first { it["id"] == "756135" }
                warsaw["name"] shouldBe "Warsaw"
            }
        }

        When("searching for 'War' with language de") {
            val response = restClient.get("/cities/suggestions?q=War&lang=de")

            Then("Warsaw should have its German name") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                val warsaw = results.first { it["id"] == "756135" }
                warsaw["name"] shouldBe "Warschau"
            }
        }

        When("searching for 'War' with language pl-PL") {
            val response = restClient.get("/cities/suggestions?q=War&lang=pl-PL")

            Then("Warsaw should have localized Polish region name") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                val warsaw = results.first { it["id"] == "756135" }
                warsaw["name"] shouldBe "Warszawa"
                warsaw["region"] shouldBe "Województwo mazowieckie"
            }
        }

        When("searching with a query shorter than 2 characters") {
            val response = restClient.get("/cities/suggestions?q=W")

            Then("it should return empty list") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                results.shouldBeEmpty()
            }
        }

        When("population boosting ranks larger cities first") {
            val response = restClient.get("/cities/suggestions?q=War&cc=PL")

            Then("Warsaw should appear before Warta") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                results shouldHaveAtLeastSize 2
                val ids = results.map { it["id"] }
                val warsawIdx = ids.indexOf("756135")
                val wartaIdx = ids.indexOf("6942553")
                (warsawIdx < wartaIdx) shouldBe true
            }
        }

        When("searching with diacritics-insensitive query 'gdansk'") {
            val response = restClient.get("/cities/suggestions?q=gdansk")

            Then("it should find Gdańsk") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                results.shouldNotBeEmpty()
                results.any { it["id"] == "3099434" } shouldBe true
            }
        }

        When("searching with limit parameter") {
            val response = restClient.get("/cities/suggestions?q=War&limit=1")

            Then("it should return at most 1 result") {
                response.statusCode shouldBe 200
                val results = response.bodyAs<List<Map<String, Any>>>()!!
                results.size shouldBe 1
            }
        }
    }

    Given("the city by ID endpoint") {

        When("getting an existing city by ID") {
            val response = restClient.get("/cities/756135")

            Then("it should return the city details with admin division info") {
                response.statusCode shouldBe 200
                val city = response.bodyAs<Map<String, Any>>()!!
                city["id"] shouldBe "756135"
                city["countryCode"] shouldBe "PL"
                city["timezone"] shouldBe "Europe/Warsaw"
                (city["population"] as Number).toLong() shouldBe 1_790_658L
                city["region"] shouldBe "Masovian Voivodeship"
                city["district"] shouldBe "Warsaw"
            }
        }

        When("getting an existing city with language localization") {
            val response = restClient.get("/cities/756135?lang=pl-PL")

            Then("the name and region should be localized to Polish") {
                response.statusCode shouldBe 200
                val city = response.bodyAs<Map<String, Any>>()!!
                city["name"] shouldBe "Warszawa"
                city["region"] shouldBe "Województwo mazowieckie"
            }
        }

        When("getting a non-existent city") {
            val response = restClient.get("/cities/999999999")

            Then("it should return 404") {
                response.statusCode shouldBe 404
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe false
                (body["status"] as String) shouldContain "CITY_NOT_FOUND"
            }
        }
    }
}) {
    companion object {
        private val testIndexDir = Files.createTempDirectory("cities-integration-test-index")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("gimlee.location.geonames.index-path") { testIndexDir.toString() }
        }
    }

    @Autowired
    lateinit var citySearchIndex: CitySearchIndex

    init {
        beforeSpec {
            // Clear city collections
            mongoTemplate.dropCollection(CityDocument.COLLECTION_NAME)
            mongoTemplate.dropCollection(CityNameDocument.COLLECTION_NAME)

            // Insert sample cities (with admin division data)
            val cities = listOf(
                cityDoc("756135", "Warszawa", "Warsaw", "PL", "78", 52.22977, 21.01178, 1_790_658, "Europe/Warsaw",
                    adm2 = "1465", adm1Nm = "Masovian Voivodeship", adm2Nm = "Warsaw", adm1Gid = "858787", adm2Gid = "756135"),
                cityDoc("2950159", "Berlin", "Berlin", "DE", "16", 52.52437, 13.41053, 3_644_826, "Europe/Berlin",
                    adm1Nm = "Berlin", adm1Gid = "2950157"),
                cityDoc("6942553", "Warta", "Warta", "PL", "10", 51.71058, 18.62875, 3_200, "Europe/Warsaw",
                    adm1Nm = "Łódź Voivodeship", adm1Gid = "858789"),
                cityDoc("3081368", "Wrocław", "Wroclaw", "PL", "02", 51.10000, 17.03333, 640_000, "Europe/Warsaw",
                    adm1Nm = "Lower Silesian Voivodeship", adm1Gid = "858788"),
                cityDoc("3099434", "Gdańsk", "Gdansk", "PL", "82", 54.35205, 18.64637, 461_935, "Europe/Warsaw",
                    adm1Nm = "Pomeranian Voivodeship", adm1Gid = "858790")
            )
            mongoTemplate.insert(cities, CityDocument.COLLECTION_NAME)

            // Insert alternate names (city names + admin division translations)
            val names = listOf(
                nameDoc("1001", "756135", "en", "Warsaw", true),
                nameDoc("1002", "756135", "pl", "Warszawa", true),
                nameDoc("1003", "756135", "de", "Warschau", true),
                nameDoc("1004", "2950159", "en", "Berlin", true),
                nameDoc("1005", "3081368", "en", "Wroclaw", true),
                nameDoc("1006", "3081368", "de", "Breslau", true),
                nameDoc("1007", "3099434", "en", "Gdansk", true),
                nameDoc("1008", "6942553", "en", "Warta", true),
                // Admin1 translations (keyed by admin geonameId)
                nameDoc("2001", "858787", "pl", "Województwo mazowieckie", true),
                nameDoc("2002", "858787", "en", "Masovian Voivodeship", true),
                nameDoc("2003", "858789", "pl", "Województwo łódzkie", true),
                nameDoc("2004", "858790", "pl", "Województwo pomorskie", true)
            )
            mongoTemplate.insert(names, CityNameDocument.COLLECTION_NAME)

            // Build the Lucene index from seeded data
            citySearchIndex.rebuildIndex()
        }

        afterSpec {
            testIndexDir.toFile().deleteRecursively()
        }
    }
}

private fun cityDoc(
    id: String, name: String, ascii: String, cc: String, adm1: String,
    lat: Double, lon: Double, pop: Long, tz: String,
    adm2: String? = null, adm1Nm: String? = null, adm2Nm: String? = null,
    adm1Gid: String? = null, adm2Gid: String? = null
): Document {
    return Document()
        .append("_id", id)
        .append("nm", name)
        .append("ascii", ascii)
        .append("cc", cc)
        .append("adm1", adm1)
        .append("adm2", adm2)
        .append("adm1Nm", adm1Nm)
        .append("adm2Nm", adm2Nm)
        .append("adm1Gid", adm1Gid)
        .append("adm2Gid", adm2Gid)
        .append("lat", lat)
        .append("lon", lon)
        .append("pop", pop)
        .append("tz", tz)
        .append("mod", System.currentTimeMillis() * 1000)
}

private fun nameDoc(
    id: String, cityId: String, lang: String, name: String, preferred: Boolean
): Document {
    return Document()
        .append("_id", id)
        .append("cid", cityId)
        .append("lang", lang)
        .append("nm", name)
        .append("pref", preferred)
        .append("shrt", false)
}
