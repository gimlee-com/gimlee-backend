package com.gimlee.api

import com.gimlee.ads.domain.CategoryService
import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.toMicros
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.bson.types.ObjectId
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant

class AdminCategoryApiIntegrationTest(
    private val categoryRepository: CategoryRepository,
    private val categoryService: CategoryService
) : BaseIntegrationTest({

    val adminUserId = ObjectId.get()

    fun adminHeaders() = restClient.createAuthHeader(
        subject = adminUserId.toHexString(),
        username = "admin",
        roles = listOf("USER", "ADMIN")
    )

    beforeSpec {
        categoryRepository.clear()
        categoryService.clearCache()
    }

    Given("Admin category CRUD via HTTP") {

        When("an admin creates a root category") {
            val body = mapOf(
                "name" to mapOf("en-US" to "Electronics", "pl-PL" to "Elektronika")
            )
            val response = restClient.post("/admin/categories", body, adminHeaders())

            Then("it should return 201 Created with correct status") {
                response.statusCode shouldBe 201
                response.body shouldContain "CATEGORY_CREATED"
            }
        }

        When("an admin creates a child category") {
            val roots = categoryRepository.findSiblings(null)
            val rootId = roots.first().id

            val body = mapOf(
                "name" to mapOf("en-US" to "Laptops"),
                "parentId" to rootId
            )
            val response = restClient.post("/admin/categories", body, adminHeaders())

            Then("it should return 201 Created") {
                response.statusCode shouldBe 201
                response.body shouldContain "CATEGORY_CREATED"
            }
        }

        When("an admin lists root categories") {
            categoryService.clearCache()
            val response = restClient.get("/admin/categories?depth=2", adminHeaders())

            Then("it should return the tree with children") {
                response.statusCode shouldBe 200
                response.body shouldContain "Electronics"
                response.body shouldContain "Laptops"
            }
        }

        When("an admin gets category by ID") {
            val roots = categoryRepository.findSiblings(null)
            val rootId = roots.first().id

            val response = restClient.get("/admin/categories/$rootId", adminHeaders())

            Then("it should return full category detail") {
                response.statusCode shouldBe 200
                response.body shouldContain "Electronics"
                response.body shouldContain "GML"
            }
        }

        When("an admin gets category children") {
            val roots = categoryRepository.findSiblings(null)
            val rootId = roots.first().id

            val response = restClient.get("/admin/categories/$rootId/children?depth=1", adminHeaders())

            Then("it should return children") {
                response.statusCode shouldBe 200
                response.body shouldContain "Laptops"
            }
        }

        When("an admin updates a category name") {
            val roots = categoryRepository.findSiblings(null)
            val rootId = roots.first().id

            val body = mapOf("name" to mapOf("en-US" to "Consumer Electronics"))
            val response = restClient.patch("/admin/categories/$rootId", body, adminHeaders())

            Then("it should return 200 Updated") {
                response.statusCode shouldBe 200
                response.body shouldContain "CATEGORY_UPDATED"

                val updated = categoryRepository.findById(rootId)!!
                updated.name["en-US"]!!.name shouldBe "Consumer Electronics"
            }
        }

        When("an admin searches categories") {
            categoryService.clearCache()
            val response = restClient.get("/admin/categories/search?q=Laptop&limit=10", adminHeaders())

            Then("it should return matching categories") {
                response.statusCode shouldBe 200
                response.body shouldContain "Laptops"
            }
        }
    }

    Given("Categories for reorder via API") {
        categoryRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        categoryRepository.insert(500, Category.Source(Category.Source.Type.GIMLEE, "gml-500"), null,
            mapOf("en-US" to Category.CategoryName("Alpha", "alpha")), 0, now)
        categoryRepository.insert(501, Category.Source(Category.Source.Type.GIMLEE, "gml-501"), null,
            mapOf("en-US" to Category.CategoryName("Beta", "beta")), 1, now)
        categoryService.clearCache()

        When("an admin reorders Beta up") {
            val body = mapOf("direction" to "UP")
            val response = restClient.patch("/admin/categories/501/reorder", body, adminHeaders())

            Then("it should return 200 Reordered") {
                response.statusCode shouldBe 200
                response.body shouldContain "CATEGORY_REORDERED"
            }
        }

        When("an admin reorders first category up (boundary)") {
            categoryService.clearCache()
            val siblings = categoryRepository.findSiblings(null).sortedBy { it.displayOrder }
            val firstId = siblings.first().id

            val body = mapOf("direction" to "UP")
            val response = restClient.patch("/admin/categories/$firstId/reorder", body, adminHeaders())

            Then("it should return 400 boundary error") {
                response.statusCode shouldBe 400
                response.body shouldContain "CATEGORY_ALREADY_AT_BOUNDARY"
            }
        }
    }

    Given("Categories for move via API") {
        categoryRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        categoryRepository.insert(600, Category.Source(Category.Source.Type.GIMLEE, "gml-600"), null,
            mapOf("en-US" to Category.CategoryName("Source", "source")), 0, now)
        categoryRepository.insert(601, Category.Source(Category.Source.Type.GIMLEE, "gml-601"), null,
            mapOf("en-US" to Category.CategoryName("Target", "target")), 1, now)
        categoryRepository.insert(602, Category.Source(Category.Source.Type.GIMLEE, "gml-602"), 600,
            mapOf("en-US" to Category.CategoryName("Child", "child")), 0, now)
        categoryService.clearCache()

        When("an admin moves a category") {
            val body = mapOf("newParentId" to 601)
            val response = restClient.patch("/admin/categories/602/move", body, adminHeaders())

            Then("it should return 200 Moved") {
                response.statusCode shouldBe 200
                response.body shouldContain "CATEGORY_MOVED"
                val moved = categoryRepository.findById(602)!!
                moved.parent shouldBe 601
            }
        }
    }

    Given("A GML leaf category for delete via API") {
        categoryRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        categoryRepository.insert(700, Category.Source(Category.Source.Type.GIMLEE, "gml-700"), null,
            mapOf("en-US" to Category.CategoryName("Deletable", "deletable")), 0, now)
        categoryService.clearCache()

        When("an admin deletes a GML category with no children and no ads") {
            val response = restClient.delete("/admin/categories/700", adminHeaders())

            Then("it should return 200 Deleted") {
                response.statusCode shouldBe 200
                response.body shouldContain "CATEGORY_DELETED"
                categoryRepository.findById(700) shouldBe null
            }
        }
    }

    Given("A GPT category for delete protection via API") {
        categoryRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        categoryRepository.upsertCategoryBySourceType(
            Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, 800, "gpt-800", null,
            mapOf("en-US" to Category.CategoryName("GPT Category", "gpt-category")), now
        )
        categoryService.clearCache()

        When("an admin attempts to delete a GPT category") {
            val response = restClient.delete("/admin/categories/800", adminHeaders())

            Then("it should return 400 with GPT delete forbidden") {
                response.statusCode shouldBe 400
                response.body shouldContain "CATEGORY_DELETE_GPT_FORBIDDEN"
            }
        }
    }
}) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun setProperties(registry: DynamicPropertyRegistry) {
            registry.add("gimlee.ads.category.sync.enabled") { "false" }
            registry.add("gimlee.auth.jwt.enabled") { "true" }
            registry.add("gimlee.auth.rest.jwt.issuer") { "test-issuer" }
            registry.add("gimlee.auth.rest.jwt.key") { "test-key-must-be-at-least-32-chars-long-!!!-123456" }
        }
    }
}
