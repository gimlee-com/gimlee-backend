package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.UUIDv7
import io.kotest.matchers.shouldBe

class CategoryServiceIntegrationTest(
    private val categoryService: CategoryService,
    private val categoryRepository: CategoryRepository
) : BaseIntegrationTest({

    Given("Some categories in the database") {
        val now = System.currentTimeMillis()
        val rootId = UUIDv7.generate()
        val childId = UUIDv7.generate()
        val grandchildId = UUIDv7.generate()

        categoryRepository.upsertGptCategory(
            rootId, "1", null, mapOf(
                "en-US" to Category.CategoryName("Animals", "animals"),
                "pl-PL" to Category.CategoryName("Zwierzęta", "zwierzeta")
            ), now
        )
        categoryRepository.upsertGptCategory(
            childId, "2", rootId, mapOf(
                "en-US" to Category.CategoryName("Dogs", "dogs"),
                "pl-PL" to Category.CategoryName("Psy", "psy")
            ), now
        )
        categoryRepository.upsertGptCategory(
            grandchildId, "3", childId, mapOf(
                "en-US" to Category.CategoryName("Food", "food"),
                "pl-PL" to Category.CategoryName("Karma", "karma")
            ), now
        )

        When("fetching full category path for grandchild in en-US") {
            val path = categoryService.getFullCategoryPath(grandchildId.toString(), "en-US")
            Then("it should return the correct breadcrumb") {
                path?.size shouldBe 3
                path?.get(0)?.name shouldBe "Animals"
                path?.get(1)?.name shouldBe "Dogs"
                path?.get(2)?.name shouldBe "Food"
            }
        }

        When("fetching full category path for grandchild in pl-PL") {
            val path = categoryService.getFullCategoryPath(grandchildId.toString(), "pl-PL")
            Then("it should return the correct breadcrumb in Polish") {
                path?.size shouldBe 3
                path?.get(0)?.name shouldBe "Zwierzęta"
                path?.get(1)?.name shouldBe "Psy"
                path?.get(2)?.name shouldBe "Karma"
            }
        }

        When("fetching full category path for grandchild in de-DE (fallback)") {
            val path = categoryService.getFullCategoryPath(grandchildId.toString(), "de-DE")
            Then("it should return the correct breadcrumb in en-US as fallback") {
                path?.size shouldBe 3
                path?.get(0)?.name shouldBe "Animals"
            }
        }

        When("fetching bulk category paths") {
            val paths = categoryService.getFullCategoryPaths(setOf(rootId.toString(), grandchildId.toString()), "en-US")
            Then("it should return a map with correct paths") {
                paths[rootId.toString()]?.size shouldBe 1
                paths[rootId.toString()]?.get(0)?.name shouldBe "Animals"
                paths[grandchildId.toString()]?.size shouldBe 3
                paths[grandchildId.toString()]?.get(2)?.name shouldBe "Food"
            }
        }

        When("checking if categories are leaf") {
            Then("root should not be a leaf") {
                categoryService.isLeaf(rootId) shouldBe false
            }
            Then("child should not be a leaf") {
                categoryService.isLeaf(childId) shouldBe false
            }
            Then("grandchild should be a leaf") {
                categoryService.isLeaf(grandchildId) shouldBe true
            }
            Then("non-existent category should not be a leaf") {
                categoryService.isLeaf(UUIDv7.generate()) shouldBe false
            }
        }
    }
})
