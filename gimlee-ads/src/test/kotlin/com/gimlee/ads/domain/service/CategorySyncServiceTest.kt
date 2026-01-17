package com.gimlee.ads.domain.service

import com.gimlee.ads.persistence.CategoryRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*

class CategorySyncServiceTest : BehaviorSpec({

    val categoryRepository = mockk<CategoryRepository>()
    val taxonomyDownloader = mockk<TaxonomyDownloader>()
    val service = CategorySyncService(categoryRepository, taxonomyDownloader, "http://localhost/test", listOf("en-US"))

    Given("A valid taxonomy file content") {
        val lines = listOf(
            "# Google Product Taxonomy",
            "1 - Animals & Pet Supplies",
            "3237 - Animals & Pet Supplies > Live Animals",
            "2 - Animals & Pet Supplies > Pet Supplies", // Child appearing after parent
            "50 - Electronics > Audio", // Child appearing before parent (if parent was later)
            "49 - Electronics"
        )

        every { taxonomyDownloader.download(any()) } returns lines
        every { categoryRepository.getGptSourceIdToUuidMap() } returns emptyMap()
        every { categoryRepository.upsertGptCategory(any(), any(), any(), any(), any()) } just Runs
        every { categoryRepository.deprecateMissingGptCategories(any()) } just Runs

        When("syncing categories") {
            service.syncCategories()

            Then("it should upsert all categories correctly resolving parents") {
                verify(exactly = 5) {
                    categoryRepository.upsertGptCategory(any(), any(), any(), any(), any())
                }

                // Verify structure
                // 1 -> Root
                verify { categoryRepository.upsertGptCategory(any(), "1", null, any(), any()) }

                // 3237 -> Parent is 1
                verify { categoryRepository.upsertGptCategory(any(), "3237", any(), any(), any()) }

                // 50 -> Parent is 49. Even if 50 came before 49 in file, pass 2 should resolve it.
                verify { categoryRepository.upsertGptCategory(any(), "50", any(), any(), any()) }
                verify { categoryRepository.upsertGptCategory(any(), "49", null, any(), any()) }
            }
        }
    }
})
