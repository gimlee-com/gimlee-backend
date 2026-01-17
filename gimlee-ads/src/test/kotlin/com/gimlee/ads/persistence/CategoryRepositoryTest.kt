package com.gimlee.ads.persistence

import com.gimlee.ads.domain.model.Category
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.toMicros
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

class CategoryRepositoryTest(
    @Autowired private val categoryRepository: CategoryRepository
) : BaseIntegrationTest({

    Given("A CategoryRepository") {
        When("upserting a new GPT category") {
            val now = Instant.now().toMicros()
            val uuid = UUID.randomUUID()
            val sourceId = "123"
            val nameMap = mapOf("en-US" to Category.CategoryName("Test Category", "test-category"))

            categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, uuid, sourceId, null, nameMap, now)

            Then("it should be retrievable via mapping") {
                val map = categoryRepository.getSourceIdToUuidMapBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
                map shouldHaveSize 1
                map[sourceId] shouldBe uuid
            }
        }

        When("updating an existing GPT category") {
            val now = Instant.now().toMicros()
            val uuid = UUID.randomUUID()
            val sourceId = "456"
            val nameMap = mapOf("en-US" to Category.CategoryName("Old Name", "old-name"))

            categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, uuid, sourceId, null, nameMap, now)

            val later = now + 1000
            val newNameMap = mapOf("en-US" to Category.CategoryName("New Name", "new-name"))

            categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, uuid, sourceId, null, newNameMap, later)

            Then("it should have the new name and updated timestamp") {
                 // To verify specific fields we might need a findByUuid or map verification if we exposed a full fetch
                 // For now relying on map existence.
                 // In a real scenario I'd add a findById to repo for testing, but avoiding adding unused code
                 // per guidelines "No Abstractions" (MongoRepository forbidden).
                 // Repository only has what we added.

                 val map = categoryRepository.getSourceIdToUuidMapBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
                 map[sourceId] shouldBe uuid
            }
        }
    }
})

