package com.gimlee.ads.domain.service

import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.BaseIntegrationTest
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

@TestPropertySource(properties = [
    "gimlee.ads.category.sync.cron=*/1 * * * * ?",
    "gimlee.ads.category.sync.lock.at-most=PT2S",
    "gimlee.ads.category.sync.lock.at-least=PT1S",
    "gimlee.ads.gpt.languages=en-US,pl-PL",
    "spring.messages.basename=i18n/ads/messages"
])
class CategorySyncIntegrationTest(
    private val categoryRepository: CategoryRepository
) : BaseIntegrationTest({
    
    Given("Google Product Taxonomy files") {

        val enUsContent = Files.readString(Paths.get("src/integration/resources/taxonomy-with-ids.en-US.txt"))
        val plPlContent = Files.readString(Paths.get("src/integration/resources/taxonomy-with-ids.pl-PL.txt"))

        // Stubbing
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/basepages/producttype/taxonomy-with-ids.en-US.txt"))
                .willReturn(WireMock.ok(enUsContent))
        )
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/basepages/producttype/taxonomy-with-ids.pl-PL.txt"))
                .willReturn(WireMock.ok(plPlContent))
        )

        When("The sync job runs") {
            Then("categories should be inserted into the database") {
                eventually(15.seconds) {
                    val map = categoryRepository.getGptSourceIdToUuidMap()
                    map shouldHaveSize 15
                }
            }

            Then("slugs and hierarchy should be correct for both languages") {
                val categories = categoryRepository.findAllGptCategories()
                val bySourceId = categories.associateBy { it.source.id }
                
                // Root category: 1 - Animals & Pet Supplies / Zwierzęta i artykuły dla zwierząt
                val root = bySourceId["1"]
                root shouldNotBe null
                root!!.name["en-US"]?.slug shouldBe "animals-pet-supplies"
                root.name["pl-PL"]?.slug shouldBe "zwierzeta-i-artykuly-dla-zwierzat"
                root.parent shouldBe null

                // Miscellaneous child for root: 1-misc
                val rootMisc = bySourceId["1-misc"]
                rootMisc shouldNotBe null
                rootMisc!!.name["en-US"]?.name shouldBe "Miscellaneous"
                rootMisc.name["en-US"]?.slug shouldBe "miscellaneous"
                rootMisc.name["pl-PL"]?.name shouldBe "Pozostałe"
                rootMisc.name["pl-PL"]?.slug shouldBe "pozostale"
                rootMisc.parent shouldBe root.id

                // Child category: 3237 - Animals & Pet Supplies > Live Animals / Żywe zwierzęta
                val child = bySourceId["3237"]
                child shouldNotBe null
                child!!.name["en-US"]?.slug shouldBe "live-animals"
                child.name["pl-PL"]?.slug shouldBe "zywe-zwierzeta"
                child.parent shouldBe root.id

                // Deep child: 3 - Animals & Pet Supplies > ... > Bird Cages & Stands / Klatki i stojaki dla ptaków
                val deepChild = bySourceId["3"]
                deepChild shouldNotBe null
                deepChild!!.name["en-US"]?.slug shouldBe "bird-cages-stands"
                deepChild.name["pl-PL"]?.slug shouldBe "klatki-i-stojaki-dla-ptakow"
                
                val birdSupplies = bySourceId["3124"]
                birdSupplies shouldNotBe null
                deepChild.parent shouldBe birdSupplies!!.id
            }
        }
    }
}) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("gimlee.ads.gpt.url-template") {
                "http://localhost:${BaseIntegrationTest.wireMockServer.port()}/basepages/producttype/taxonomy-with-ids.%s.txt"
            }
        }
    }
}
