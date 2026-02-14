package com.gimlee.ads.domain

import com.mongodb.client.MongoDatabase
import org.bson.Document
import com.gimlee.ads.domain.service.CategorySyncService
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.BaseIntegrationTest
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.matchers.shouldBe
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

private const val ROOT_ID = 1
private const val CHILD_ID = 2
private const val GRANDCHILD_ID = 3

class CategoryServiceIntegrationTest(
    private val categoryService: CategoryService,
    private val categoryRepository: CategoryRepository,
    private val categorySyncService: CategorySyncService,
    private val mongoDatabase: MongoDatabase
) : BaseIntegrationTest({

    beforeSpec {
        wireMockServer.resetAll()
        categoryRepository.clear()
        mongoDatabase.getCollection("gimlee-shedlock").deleteMany(Document())
        categoryService.clearCache()
        setupWiremock()
        categorySyncService.syncCategories()
    }

    Given("Some categories in the database") {

        When("fetching full category path for grandchild in en-US") {
            val path = categoryService.getFullCategoryPath(GRANDCHILD_ID, "en-US")
            Then("it should return the correct breadcrumb") {
                path?.size shouldBe 3
                path?.get(0)?.name shouldBe "Animals"
                path?.get(1)?.name shouldBe "Dogs"
                path?.get(2)?.name shouldBe "Food"
            }
        }

        When("fetching full category path for grandchild in pl-PL") {
            val path = categoryService.getFullCategoryPath(GRANDCHILD_ID, "pl-PL")
            Then("it should return the correct breadcrumb in Polish") {
                path?.size shouldBe 3
                path?.get(0)?.name shouldBe "Zwierzęta"
                path?.get(1)?.name shouldBe "Psy"
                path?.get(2)?.name shouldBe "Karma"
            }
        }

        When("fetching full category path for grandchild in de-DE (fallback)") {
            val path = categoryService.getFullCategoryPath(GRANDCHILD_ID, "de-DE")
            Then("it should return the correct breadcrumb in en-US as fallback") {
                path?.size shouldBe 3
                path?.get(0)?.name shouldBe "Animals"
            }
        }

        When("fetching bulk category paths") {
            val paths = categoryService.getFullCategoryPaths(setOf(ROOT_ID, GRANDCHILD_ID), "en-US")
            Then("it should return a map with correct paths") {
                paths[ROOT_ID]?.size shouldBe 1
                paths[ROOT_ID]?.get(0)?.name shouldBe "Animals"
                paths[GRANDCHILD_ID]?.size shouldBe 3
                paths[GRANDCHILD_ID]?.get(2)?.name shouldBe "Food"
            }
        }

        When("checking if categories are leaf") {
            Then("root should not be a leaf") {
                categoryService.isLeaf(ROOT_ID) shouldBe false
            }
            Then("child should not be a leaf") {
                categoryService.isLeaf(CHILD_ID) shouldBe false
            }
            Then("grandchild should be a leaf") {
                categoryService.isLeaf(GRANDCHILD_ID) shouldBe true
            }
            Then("non-existent category should not be a leaf") {
                categoryService.isLeaf(999) shouldBe false
            }
        }
    }
}) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun setProperties(registry: DynamicPropertyRegistry) {
            registry.add("gimlee.ads.category.sync.enabled") { "true" }
            registry.add("gimlee.ads.category.sync.lock.at-least") { "PT0S" }
            registry.add("spring.messages.basename") { "i18n/ads/messages" }
            registry.add("gimlee.ads.gpt.url-template") { "http://localhost:${wireMockServer.port()}/basepages/producttype/taxonomy-with-ids.%s.txt" }
            setupWiremock()
        }

        private fun setupWiremock() {
            val enUsTaxonomy = """
            1 - Animals
            2 - Animals > Dogs
            3 - Animals > Dogs > Food
            """.trimIndent()

            val plPlTaxonomy = """
            1 - Zwierzęta
            2 - Zwierzęta > Psy
            3 - Zwierzęta > Psy > Karma
            """.trimIndent()

            wireMockServer.stubFor(
                get(urlPathEqualTo("/basepages/producttype/taxonomy-with-ids.en-US.txt"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/plain")
                            .withBody(enUsTaxonomy)
                    )
            )

            wireMockServer.stubFor(
                get(urlPathEqualTo("/basepages/producttype/taxonomy-with-ids.pl-PL.txt"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/plain")
                            .withBody(plPlTaxonomy)
                    )
            )
        }
    }
}
