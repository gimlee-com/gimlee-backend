package com.gimlee.location.cities.geonames.parser

import com.gimlee.location.cities.geonames.config.GeoNamesProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class GeoNamesParserTest : BehaviorSpec({

    val properties = GeoNamesProperties()
    val parser = GeoNamesParser(properties)
    val tmpDir = Files.createTempDirectory("geonames-parser-test")

    fun writeTmpFile(name: String, content: String): Path {
        val file = tmpDir.resolve(name)
        Files.writeString(file, content)
        return file
    }

    afterSpec {
        tmpDir.toFile().deleteRecursively()
    }

    Given("a cities TSV file") {

        When("parsing a valid city line") {
            Then("it should parse all fields correctly") {
                val file = writeTmpFile("valid-city.txt",
                    "756135\tWarsaw\tWarsaw\tVarsava,Varsovia,Warschau,Warszawa\t52.22977\t21.01178\tP\tPPLA\tPL\t\t78\t\t\t\t1790658\t\t113\tEurope/Warsaw\t2024-01-15"
                )
                val cities = parser.parseCities(file).toList()
                cities shouldHaveSize 1
                val city = cities.first()
                city.geonameId shouldBe "756135"
                city.name shouldBe "Warsaw"
                city.asciiName shouldBe "Warsaw"
                city.countryCode shouldBe "PL"
                city.admin1Code shouldBe "78"
                city.admin2Code.shouldBeNull()
                city.latitude shouldBe 52.22977
                city.longitude shouldBe 21.01178
                city.population shouldBe 1790658L
                city.timezone shouldBe "Europe/Warsaw"
                city.modificationDate shouldBe "2024-01-15"
            }
        }

        When("parsing a line with fewer than 19 fields") {
            Then("it should skip the line") {
                val file = writeTmpFile("short-line.txt", "756135\tWarsaw\tWarsaw")
                val cities = parser.parseCities(file).toList()
                cities.shouldBeEmpty()
            }
        }

        When("parsing multiple lines with mixed validity") {
            Then("it should parse valid lines and skip invalid ones") {
                val content = listOf(
                    "756135\tWarsaw\tWarsaw\tVarsava\t52.22977\t21.01178\tP\tPPLA\tPL\t\t78\t\t\t\t1790658\t\t113\tEurope/Warsaw\t2024-01-15",
                    "short\tline",
                    "2950159\tBerlin\tBerlin\tBerlino\t52.52437\t13.41053\tP\tPPLC\tDE\t\t16\t\t\t\t3644826\t\t34\tEurope/Berlin\t2024-01-15"
                ).joinToString("\n")
                val file = writeTmpFile("mixed-validity.txt", content)
                val cities = parser.parseCities(file).toList()
                cities shouldHaveSize 2
                cities[0].name shouldBe "Warsaw"
                cities[1].name shouldBe "Berlin"
            }
        }

        When("loading city IDs") {
            Then("it should extract the first column as IDs") {
                val file = writeTmpFile("city-ids.txt", "756135\tWarsaw\trest\n2950159\tBerlin\trest\n")
                val ids = parser.loadCityIds(file)
                ids shouldBe setOf("756135", "2950159")
            }
        }

        When("parsing a city with blank admin1 code") {
            Then("admin1Code should be null") {
                val file = writeTmpFile("blank-admin.txt",
                    "756135\tWarsaw\tWarsaw\t\t52.22977\t21.01178\tP\tPPLA\tPL\t\t\t\t\t\t1790658\t\t113\tEurope/Warsaw\t2024-01-15"
                )
                val cities = parser.parseCities(file).toList()
                cities shouldHaveSize 1
                cities.first().admin1Code.shouldBeNull()
            }
        }
    }

    Given("an alternate names TSV file") {

        val cityIds = setOf("756135", "2950159")

        When("parsing valid alternate name lines") {
            Then("it should parse all valid entries") {
                val content = listOf(
                    "1001\t756135\ten\tWarsaw\t1\t0\t0\t0",
                    "1002\t756135\tpl\tWarszawa\t1\t0\t0\t0",
                    "1003\t2950159\tde\tBerlin\t1\t0\t0\t0"
                ).joinToString("\n")
                val file = writeTmpFile("valid-altnames.txt", content)
                val names = parser.parseAlternateNames(file, cityIds).toList()
                names shouldHaveSize 3
                names[0].alternateName shouldBe "Warsaw"
                names[0].isoLanguage shouldBe "en"
                names[0].isPreferredName shouldBe true
                names[1].alternateName shouldBe "Warszawa"
                names[2].geonameId shouldBe "2950159"
            }
        }

        When("encountering names for cities not in the cityIds set") {
            Then("it should filter them out") {
                val file = writeTmpFile("non-city-names.txt",
                    "1001\t999999\ten\tUnknown City\t1\t0\t0\t0"
                )
                val names = parser.parseAlternateNames(file, cityIds).toList()
                names.shouldBeEmpty()
            }
        }

        When("encountering invalid language codes") {
            Then("it should reject all invalid codes") {
                val content = listOf(
                    "1001\t756135\tpost\tWarsaw 02-123\t0\t0\t0\t0",
                    "1002\t756135\tlink\thttps://en.wikipedia.org/wiki/Warsaw\t0\t0\t0\t0",
                    "1003\t756135\twkdt\tQ270\t0\t0\t0\t0",
                    "1004\t756135\tiata\tWAW\t0\t0\t0\t0",
                    "1005\t756135\t1234\tNumeric\t0\t0\t0\t0",
                    "1006\t756135\t\t\t0\t0\t0\t0"
                ).joinToString("\n")
                val file = writeTmpFile("invalid-langs.txt", content)
                val names = parser.parseAlternateNames(file, cityIds).toList()
                names.shouldBeEmpty()
            }
        }

        When("encountering valid 2-letter and 3-letter language codes") {
            Then("it should accept all valid codes") {
                val content = listOf(
                    "1001\t756135\ten\tWarsaw\t0\t0\t0\t0",
                    "1002\t756135\tpl\tWarszawa\t0\t0\t0\t0",
                    "1003\t756135\tzh\t华沙\t0\t0\t0\t0",
                    "1004\t756135\tfra\tVarsovie\t0\t0\t0\t0"
                ).joinToString("\n")
                val file = writeTmpFile("valid-langs.txt", content)
                val names = parser.parseAlternateNames(file, cityIds).toList()
                names shouldHaveSize 4
            }
        }

        When("encountering colloquial and historic entries") {
            Then("it should filter out colloquial and historic, keep normal") {
                val content = listOf(
                    "1001\t756135\ten\tWarsaw\t0\t0\t1\t0",
                    "1002\t756135\ten\tOld Warsaw\t0\t0\t0\t1",
                    "1003\t756135\ten\tWarsaw\t1\t0\t0\t0"
                ).joinToString("\n")
                val file = writeTmpFile("colloquial-historic.txt", content)
                val names = parser.parseAlternateNames(file, cityIds).toList()
                names shouldHaveSize 1
                names.first().alternateName shouldBe "Warsaw"
                names.first().isPreferredName shouldBe true
            }
        }
    }

    Given("admin code files") {

        When("parsing admin1 codes") {
            Then("it should parse all fields correctly") {
                val content = listOf(
                    "PL.78\tMazovia\tMazovia\t858787",
                    "PL.02\tLower Silesia\tLower Silesia\t858788",
                    "DE.16\tBerlin\tBerlin\t2950157"
                ).joinToString("\n")
                val file = writeTmpFile("admin1codes.txt", content)
                val result = parser.parseAdminCodes(file)
                result.size shouldBe 3
                result["PL.78"]!!.name shouldBe "Mazovia"
                result["PL.78"]!!.geonameId shouldBe "858787"
                result["DE.16"]!!.name shouldBe "Berlin"
            }
        }

        When("parsing admin2 codes with fully qualified keys") {
            Then("it should parse codes with compound keys") {
                val content = listOf(
                    "PL.78.1465\tPowiat Toruński\tPowiat Torunski\t7531924",
                    "PL.78.1461\tWarsaw\tWarsaw\t756135"
                ).joinToString("\n")
                val file = writeTmpFile("admin2codes.txt", content)
                val result = parser.parseAdminCodes(file)
                result.size shouldBe 2
                result["PL.78.1465"]!!.name shouldBe "Powiat Toruński"
                result["PL.78.1465"]!!.asciiName shouldBe "Powiat Torunski"
                result["PL.78.1465"]!!.geonameId shouldBe "7531924"
            }
        }

        When("parsing lines with missing fields") {
            Then("it should skip them gracefully") {
                val content = listOf(
                    "PL.78\tMazovia\tMazovia\t858787",
                    "PL.02\tLower Silesia",
                    "\t\t\t"
                ).joinToString("\n")
                val file = writeTmpFile("admin-partial.txt", content)
                val result = parser.parseAdminCodes(file)
                result.size shouldBe 1
                result["PL.78"]!!.geonameId shouldBe "858787"
            }
        }

        When("parsing a city with admin2 code") {
            Then("admin2Code should be captured") {
                val file = writeTmpFile("city-with-admin2.txt",
                    "756135\tWarsaw\tWarsaw\tVarsava\t52.22977\t21.01178\tP\tPPLA\tPL\t\t78\t1465\t\t\t1790658\t\t113\tEurope/Warsaw\t2024-01-15"
                )
                val cities = parser.parseCities(file).toList()
                cities shouldHaveSize 1
                cities.first().admin1Code shouldBe "78"
                cities.first().admin2Code shouldBe "1465"
            }
        }
    }
})
