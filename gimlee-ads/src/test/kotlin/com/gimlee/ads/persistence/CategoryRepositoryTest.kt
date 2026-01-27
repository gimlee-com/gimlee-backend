package com.gimlee.ads.persistence

import com.gimlee.ads.domain.model.Category
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.toMicros
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class CategoryRepositoryTest(
    @Autowired private val categoryRepository: CategoryRepository
) : BaseIntegrationTest({

    beforeSpec {
        categoryRepository.clear()
    }

    Given("A CategoryRepository") {
        When("upserting a new GPT category") {
            val now = Instant.now().toMicros()
            val id = 123
            val sourceId = "123"
            val nameMap = mapOf("en-US" to Category.CategoryName("Test Category", "test-category"))

            categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, id, sourceId, null, nameMap, now)

            Then("it should be retrievable via mapping") {
                val map = categoryRepository.getSourceIdToIdMapBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
                map shouldHaveSize 1
                map[sourceId] shouldBe id
            }
        }

        When("updating an existing GPT category") {
            val now = Instant.now().toMicros()
            val id = 456
            val sourceId = "456"
            val nameMap = mapOf("en-US" to Category.CategoryName("Old Name", "old-name"))

            categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, id, sourceId, null, nameMap, now)

            val later = now + 1000
            val newNameMap = mapOf("en-US" to Category.CategoryName("New Name", "new-name"))

            categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, id, sourceId, null, newNameMap, later)

            Then("it should have the new name and updated timestamp") {
                 val map = categoryRepository.getSourceIdToIdMapBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
                 map[sourceId] shouldBe id
            }
        }
    }
})

