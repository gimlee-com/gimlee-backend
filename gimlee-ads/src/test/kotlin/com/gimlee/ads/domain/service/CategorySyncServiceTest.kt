package com.gimlee.ads.domain.service

import com.gimlee.ads.persistence.CategoryRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*
import org.springframework.context.MessageSource

class CategorySyncServiceTest : BehaviorSpec({

    val categoryRepository = mockk<CategoryRepository>()
    val taxonomyDownloader = mockk<TaxonomyDownloader>()
    val messageSource = mockk<MessageSource>()

    Given("A valid taxonomy file content") {
        clearMocks(categoryRepository, taxonomyDownloader, messageSource)
        val service = CategorySyncService(categoryRepository, taxonomyDownloader, messageSource, "http://localhost/test", listOf("en-US"), true)
        val lines = listOf(
            "# Google Product Taxonomy",
            "1 - Animals & Pet Supplies",
            "3237 - Animals & Pet Supplies > Live Animals",
            "2 - Animals & Pet Supplies > Pet Supplies", // Child appearing after parent
            "50 - Electronics > Audio", // Child appearing before parent (if parent was later)
            "49 - Electronics"
        )

        every { taxonomyDownloader.download(any()) } returns lines
        every { messageSource.getMessage("category.gpt.miscellaneous", null, any()) } returns "Miscellaneous"
        every { categoryRepository.getSourceIdToIdMapBySourceType(any()) } returns emptyMap()
        every { categoryRepository.getMaxId() } returns 0
        every { categoryRepository.upsertCategoryBySourceType(any(), any(), any(), any(), any(), any()) } just Runs
        every { categoryRepository.deprecateMissingCategoriesBySourceType(any(), any()) } just Runs

        When("syncing categories") {
            service.syncCategories()

            Then("it should upsert all categories correctly resolving parents") {
                verify(exactly = 7) {
                    categoryRepository.upsertCategoryBySourceType(any(), any(), any(), any(), any(), any())
                }

                // Verify structure
                // 1 -> Root
                verify { categoryRepository.upsertCategoryBySourceType(any(), any(), "1", null, any(), any()) }

                // 3237 -> Parent is 1
                verify { categoryRepository.upsertCategoryBySourceType(any(), any(), "3237", any(), any(), any()) }

                // 50 -> Parent is 49. Even if 50 came before 49 in file, pass 2 should resolve it.
                verify { categoryRepository.upsertCategoryBySourceType(any(), any(), "50", any(), any(), any()) }
                verify { categoryRepository.upsertCategoryBySourceType(any(), any(), "49", null, any(), any()) }
            }
        }
    }

    Given("The sync is disabled") {
        clearMocks(categoryRepository, taxonomyDownloader, messageSource)
        val disabledService = CategorySyncService(categoryRepository, taxonomyDownloader, messageSource, "http://localhost/test", listOf("en-US"), false)

        When("syncing categories") {
            disabledService.syncCategories()

            Then("it should do nothing") {
                verify(exactly = 0) { categoryRepository.getSourceIdToIdMapBySourceType(any()) }
            }
        }
    }
})
