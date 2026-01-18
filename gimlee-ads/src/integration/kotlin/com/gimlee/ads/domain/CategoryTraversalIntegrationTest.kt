package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CategoryTraversalIntegrationTest(
    private val categoryService: CategoryService,
    private val categoryRepository: CategoryRepository
) : BaseIntegrationTest({

    beforeSpec {
        categoryRepository.clear()
        categoryService.clearCache()
    }

    Given("A hierarchy of categories") {
        val now = System.currentTimeMillis()
        
        // 1: Animals
        //   2: Dogs
        //     4: Bulldogs
        //   3: Cats
        // 5: Electronics
        
        categoryRepository.upsertCategoryBySourceType(
            Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY,
            1, "1", null, mapOf("en-US" to Category.CategoryName("Animals", "animals")), now
        )
        categoryRepository.upsertCategoryBySourceType(
            Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY,
            2, "2", 1, mapOf("en-US" to Category.CategoryName("Dogs", "dogs")), now
        )
        categoryRepository.upsertCategoryBySourceType(
            Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY,
            3, "3", 1, mapOf("en-US" to Category.CategoryName("Cats", "cats")), now
        )
        categoryRepository.upsertCategoryBySourceType(
            Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY,
            4, "4", 2, mapOf("en-US" to Category.CategoryName("Bulldogs", "bulldogs")), now
        )
        categoryRepository.upsertCategoryBySourceType(
            Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY,
            5, "5", null, mapOf("en-US" to Category.CategoryName("Electronics", "electronics")), now
        )

        When("fetching roots with depth 1") {
            val roots = categoryService.getChildren(null, 1, "en-US")
            Then("it should return only root categories without children") {
                roots shouldHaveSize 2
                roots.any { it.name == "Animals" } shouldBe true
                roots.any { it.name == "Electronics" } shouldBe true
                roots.forEach { it.children shouldBe null }
            }
        }

        When("fetching roots with depth 2") {
            val roots = categoryService.getChildren(null, 2, "en-US")
            Then("it should return roots with their immediate children") {
                roots shouldHaveSize 2
                val animals = roots.find { it.name == "Animals" }!!
                animals.children shouldNotBe null
                animals.children!! shouldHaveSize 2
                animals.children!!.any { it.name == "Dogs" } shouldBe true
                animals.children!!.any { it.name == "Cats" } shouldBe true
                
                // Grandchildren should not be populated
                animals.children!!.forEach { it.children shouldBe null }
            }
        }

        When("fetching children of Animals (id=1) with depth 1") {
            val children = categoryService.getChildren(1, 1, "en-US")
            Then("it should return Dogs and Cats") {
                children shouldHaveSize 2
                children.any { it.name == "Dogs" } shouldBe true
                children.any { it.name == "Cats" } shouldBe true
                children.forEach { it.children shouldBe null }
            }
        }

        When("fetching children of Animals (id=1) with depth 2") {
            val children = categoryService.getChildren(1, 2, "en-US")
            Then("it should return Dogs with Bulldogs and Cats with no children") {
                children shouldHaveSize 2
                val dogs = children.find { it.name == "Dogs" }!!
                dogs.children shouldNotBe null
                dogs.children!! shouldHaveSize 1
                dogs.children!![0].name shouldBe "Bulldogs"
                
                val cats = children.find { it.name == "Cats" }!!
                cats.children shouldNotBe null
                cats.children!! shouldHaveSize 0
            }
        }
        
        When("checking hasChildren flag") {
            val roots = categoryService.getChildren(null, 1, "en-US")
            Then("Animals should have children, Electronics should not") {
                roots.find { it.name == "Animals" }?.hasChildren shouldBe true
                roots.find { it.name == "Electronics" }?.hasChildren shouldBe false
            }
            
            val dogsChildren = categoryService.getChildren(2, 1, "en-US")
            Then("Dogs children (Bulldogs) should not have children") {
                 dogsChildren.find { it.name == "Bulldogs" }?.hasChildren shouldBe false
            }
        }
    }
})
