package com.gimlee.location.cities.geonames.index

import com.gimlee.location.cities.geonames.config.GeoNamesProperties
import com.gimlee.location.cities.persistence.CityRepository
import com.gimlee.location.cities.persistence.model.CityDocument
import com.gimlee.location.cities.persistence.model.CityNameDocument
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files

class CitySearchIndexTest : BehaviorSpec({

    val testIndexPath = Files.createTempDirectory("lucene-test-index")
    val properties = GeoNamesProperties(
        indexPath = testIndexPath.toString(),
        supportedLanguages = setOf("en", "pl", "de", "fr")
    )

    val sampleCities = listOf(
        CityDocument("756135", "Warszawa", "Warsaw", "PL", "78", null, "Masovian Voivodeship", null, "858787", null, 52.22977, 21.01178, 1_790_658, "Europe/Warsaw", 0L),
        CityDocument("2950159", "Berlin", "Berlin", "DE", "16", null, "Berlin", null, "2950157", null, 52.52437, 13.41053, 3_644_826, "Europe/Berlin", 0L),
        CityDocument("6942553", "Warta", "Warta", "PL", "10", null, "Łódź Voivodeship", null, "858789", null, 51.71058, 18.62875, 3_200, "Europe/Warsaw", 0L),
        CityDocument("3054643", "Budapest", "Budapest", "HU", "05", null, "Budapest", null, "3054638", null, 47.49835, 19.04045, 1_752_286, "Europe/Budapest", 0L),
        CityDocument("3081368", "Wrocław", "Wroclaw", "PL", "02", null, "Lower Silesian Voivodeship", null, "858788", null, 51.10000, 17.03333, 640_000, "Europe/Warsaw", 0L),
        CityDocument("3099434", "Gdańsk", "Gdansk", "PL", "82", null, "Pomeranian Voivodeship", null, "858790", null, 54.35205, 18.64637, 461_935, "Europe/Warsaw", 0L),
        CityDocument("3088171", "São Paulo", "Sao Paulo", "BR", "27", null, "São Paulo", null, "3448433", null, -23.5475, -46.63611, 11_253_503, "America/Sao_Paulo", 0L)
    )

    val sampleNames = listOf(
        CityNameDocument("1001", "756135", "en", "Warsaw", true, false),
        CityNameDocument("1002", "756135", "pl", "Warszawa", true, false),
        CityNameDocument("1003", "756135", "de", "Warschau", true, false),
        CityNameDocument("1004", "2950159", "en", "Berlin", true, false),
        CityNameDocument("1005", "3081368", "en", "Wroclaw", true, false),
        CityNameDocument("1006", "3081368", "de", "Breslau", true, false),
        CityNameDocument("1007", "3088171", "en", "Sao Paulo", true, false),
        CityNameDocument("1008", "3099434", "en", "Gdansk", true, false),
        CityNameDocument("1009", "6942553", "en", "Warta", true, false)
    )

    val cityRepository = mockk<CityRepository>()

    beforeSpec {
        every { cityRepository.streamAllCities() } returns sampleCities.asSequence()
        every { cityRepository.streamAllAlternateNames() } returns sampleNames.asSequence()
    }

    val searchIndex = CitySearchIndex(properties, cityRepository)

    Given("a Lucene city search index") {

        When("the index has not been built yet") {
            Then("isReady should return false") {
                searchIndex.isReady() shouldBe false
            }
        }

        When("the index is built") {
            val indexedCount = searchIndex.rebuildIndex()

            Then("it should index all cities") {
                indexedCount shouldBe sampleCities.size.toLong()
            }

            Then("isReady should return true") {
                searchIndex.isReady() shouldBe true
            }
        }

        When("searching for 'war'") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("war", null, null, 10)

            Then("it should return Warsaw and Warta") {
                results.shouldNotBeEmpty()
                val ids = results.map { it.geonameId }.toSet()
                ids.contains("756135") shouldBe true
                ids.contains("6942553") shouldBe true
            }

            Then("Warsaw (1.8M population) should rank above Warta (3k)") {
                val warsawIdx = results.indexOfFirst { it.geonameId == "756135" }
                val wartaIdx = results.indexOfFirst { it.geonameId == "6942553" }
                (warsawIdx < wartaIdx) shouldBe true
            }

            Then("Berlin should not be in the results") {
                results.none { it.geonameId == "2950159" } shouldBe true
            }
        }

        When("searching for 'war' with country code filter PL") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("war", "PL", null, 10)

            Then("all results should be in Poland") {
                results.shouldNotBeEmpty()
                results.forEach { it.countryCode shouldBe "PL" }
            }
        }

        When("searching for 'war' with language tag 'en-US'") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("war", null, "en-US", 10)

            Then("Warsaw should have a localized English name") {
                val warsaw = results.first { it.geonameId == "756135" }
                warsaw.localizedName shouldBe "Warsaw"
            }
        }

        When("searching for 'war' with language tag 'de'") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("war", null, "de", 10)

            Then("Warsaw should have a localized German name") {
                val warsaw = results.first { it.geonameId == "756135" }
                warsaw.localizedName shouldBe "Warschau"
            }
        }

        When("searching for 'breslau' with language 'de'") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("breslau", null, "de", 10)

            Then("it should find Wrocław via its German name") {
                results.shouldNotBeEmpty()
                val wroclaw = results.first { it.geonameId == "3081368" }
                wroclaw.localizedName shouldBe "Breslau"
            }
        }

        When("searching for 'sao' (diacritics-insensitive)") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("sao", null, null, 10)

            Then("it should match São Paulo via ASCII folding") {
                results.shouldNotBeEmpty()
                results.any { it.geonameId == "3088171" } shouldBe true
            }
        }

        When("searching for 'gdansk' (diacritics-insensitive)") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("gdansk", null, null, 10)

            Then("it should match Gdańsk via ASCII folding") {
                results.shouldNotBeEmpty()
                results.any { it.geonameId == "3099434" } shouldBe true
            }
        }

        When("searching for 'ber'") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("ber", null, null, 10)

            Then("it should find Berlin") {
                results.shouldNotBeEmpty()
                results.any { it.geonameId == "2950159" } shouldBe true
            }
        }

        When("population boosting with tiered thresholds") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("b", null, null, 10)

            Then("Berlin (3.6M) should rank above Budapest (1.7M)") {
                val berlinIdx = results.indexOfFirst { it.geonameId == "2950159" }
                val budapestIdx = results.indexOfFirst { it.geonameId == "3054643" }
                if (berlinIdx >= 0 && budapestIdx >= 0) {
                    (berlinIdx < budapestIdx) shouldBe true
                }
            }
        }

        When("searching with no matches") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("zzzzxxx", null, null, 10)

            Then("it should return empty list") {
                results.shouldBeEmpty()
            }
        }

        When("limit parameter is respected") {
            searchIndex.rebuildIndex()
            val results = searchIndex.search("w", null, null, 2)

            Then("it should return at most 2 results") {
                results.size shouldBe 2
            }
        }
    }

    afterSpec {
        searchIndex.close()
        testIndexPath.toFile().deleteRecursively()
    }
})
