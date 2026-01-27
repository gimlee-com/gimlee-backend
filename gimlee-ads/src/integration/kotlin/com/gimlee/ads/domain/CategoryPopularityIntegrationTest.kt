package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.toMicros
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import java.time.Instant

class CategoryPopularityIntegrationTest(
    @Autowired private val categoryService: CategoryService,
    @Autowired private val adService: AdService,
    @Autowired private val adRepository: AdRepository,
    @Autowired private val categoryRepository: CategoryRepository,
    @Autowired private val mongoDatabase: com.mongodb.client.MongoDatabase,
    @Autowired private val groundingService: CategoryPopularityGroundingService,
    @Autowired private val eventPublisher: org.springframework.context.ApplicationEventPublisher
) : BaseIntegrationTest({

    val now = Instant.now().toMicros()

    beforeSpec {
        categoryRepository.clear()
        adRepository.clear()
        categoryService.clearCache()

        categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, 1, "c1", null, mapOf("en-US" to Category.CategoryName("Electronics", "electronics")), now)
        categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, 2, "c2", 1, mapOf("en-US" to Category.CategoryName("Computers", "computers")), now)
        categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, 3, "c3", 2, mapOf("en-US" to Category.CategoryName("Laptops", "laptops")), now)
        categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, 4, "c4", 2, mapOf("en-US" to Category.CategoryName("Tablets", "tablets")), now)
    }

    Given("A hierarchy of categories") {
        When("Searching for 'electr'") {
            val suggestions = categoryService.getSuggestions("electr", "en-US")
            println("[DEBUG_LOG] Suggestions for 'electr': ${suggestions.map { it.displayPath }}")
            
            Then("it should return leaf descendants of Electronics") {
                suggestions shouldHaveSize 2
                suggestions.map { it.id }.toSet() shouldBe setOf(3, 4)
            }
        }

        And("Ads are activated in Laptops") {
            val userId = ObjectId().toHexString()
            val ad = adService.createAd(userId, "Cool Laptop", 3, 10)
            
            // Manually activate the ad to bypass role validation in AdService
            val adDoc = adRepository.findById(ObjectId(ad.id))!!
            val activatedDoc = adDoc.copy(
                status = AdStatus.ACTIVE,
                description = "Some description",
                price = java.math.BigDecimal("1000"),
                currency = com.gimlee.common.domain.model.Currency.ARRR,
                cityId = "WAW",
                location = org.springframework.data.mongodb.core.geo.GeoJsonPoint(21.0, 52.2),
                stock = 10
            )
            adRepository.save(activatedDoc)
            
            eventPublisher.publishEvent(com.gimlee.events.AdStatusChangedEvent(
                adId = ad.id,
                oldStatus = AdStatus.INACTIVE.name,
                newStatus = AdStatus.ACTIVE.name,
                categoryIds = activatedDoc.categoryIds ?: emptyList()
            ))
            
            Then("popularity of Laptops and its ancestors should increase") {
                val cats = categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
                cats.find { it.id == 3 }?.popularity shouldBe 1L
                cats.find { it.id == 2 }?.popularity shouldBe 1L
                cats.find { it.id == 1 }?.popularity shouldBe 1L
                cats.find { it.id == 4 }?.popularity shouldBe 0L
            }
        }
        
        And("An ad is deactivated") {
            val ads = adService.getAds(AdFilters(categoryId = 3), AdSorting(com.gimlee.ads.domain.model.By.CREATED_DATE, com.gimlee.ads.domain.model.Direction.DESC), Pageable.unpaged())
            val ad = ads.content.first()
            adService.deactivateAd(ad.id, ad.userId)

            Then("popularity should decrease") {
                val cats = categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
                cats.find { it.id == 3 }?.popularity shouldBe 0L
                cats.find { it.id == 1 }?.popularity shouldBe 0L
            }
        }
        
        And("Running grounding task") {
            // Create some drifted data manually
            categoryRepository.incrementPopularity(listOf(3, 2, 1), 10L)
            
            // Activate one ad properly
            val userId = ObjectId().toHexString()
            val ad = adService.createAd(userId, "Another Laptop", 3, 5)
            val adDoc = adRepository.findById(ObjectId(ad.id))!!
            val activatedDoc = adDoc.copy(
                status = AdStatus.ACTIVE,
                description = "Some description",
                price = java.math.BigDecimal("1000"),
                currency = com.gimlee.common.domain.model.Currency.ARRR,
                cityId = "WAW",
                location = org.springframework.data.mongodb.core.geo.GeoJsonPoint(21.0, 52.2),
                stock = 5
            )
            adRepository.save(activatedDoc)
            eventPublisher.publishEvent(com.gimlee.events.AdStatusChangedEvent(
                adId = ad.id,
                oldStatus = AdStatus.INACTIVE.name,
                newStatus = AdStatus.ACTIVE.name,
                categoryIds = activatedDoc.categoryIds ?: emptyList()
            ))

            // Now Laptops should have 11 (10 drift + 1 new)
            categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY).find { it.id == 3 }?.popularity shouldBe 11L
            
            groundingService.groundPopularity()
            
            Then("popularity should be corrected based on active ads") {
                // Only 1 active ad in category 3
                val cats = categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
                cats.find { it.id == 3 }?.popularity shouldBe 1L
                cats.find { it.id == 1 }?.popularity shouldBe 1L
            }
        }
    }
})
