package com.gimlee.api

import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.ads.domain.CategoryService
import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class CategoryApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val categoryRepository: CategoryRepository,
    private val categoryService: CategoryService
) : BaseIntegrationTest({

    beforeSpec {
        categoryRepository.clear()
        categoryService.clearCache()
    }

    Given("A hierarchy of categories") {
        val now = System.currentTimeMillis()
        
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

        When("GET /ads/categories") {
            val result = mockMvc.get("/ads/categories") {
                header("Accept-Language", "en-US")
            }.andDo {
                print()
            }.andExpect {
                status { isOk() }
            }.andReturn()
            
            val content = result.response.contentAsString
            
            Then("it should return root categories") {
                content.contains("\"id\":1") shouldBe true
                content.contains("\"name\":\"Animals\"") shouldBe true
                content.contains("\"hasChildren\":true") shouldBe true
            }
        }

        When("GET /ads/categories?depth=2") {
            val result = mockMvc.get("/ads/categories?depth=2") {
                header("Accept-Language", "en-US")
            }.andExpect {
                status { isOk() }
            }.andReturn()
            
            val content = result.response.contentAsString
            
            Then("it should return roots with children") {
                content.contains("\"name\":\"Dogs\"") shouldBe true
                content.contains("\"name\":\"Cats\"") shouldBe true
            }
        }

        When("GET /ads/categories/1/children") {
            val result = mockMvc.get("/ads/categories/1/children") {
                header("Accept-Language", "en-US")
            }.andExpect {
                status { isOk() }
            }.andReturn()
            
            val content = result.response.contentAsString
            
            Then("it should return children of Animals") {
                content.contains("\"name\":\"Dogs\"") shouldBe true
                content.contains("\"name\":\"Cats\"") shouldBe true
            }
        }

        When("GET /ads/categories/2/children?depth=2") {
            val result = mockMvc.get("/ads/categories/2/children?depth=2") {
                header("Accept-Language", "en-US")
            }.andExpect {
                status { isOk() }
            }.andReturn()
            
            val content = result.response.contentAsString
            
            Then("it should return Bulldogs as grandchild") {
                content.contains("\"name\":\"Bulldogs\"") shouldBe true
            }
        }
    }
})
