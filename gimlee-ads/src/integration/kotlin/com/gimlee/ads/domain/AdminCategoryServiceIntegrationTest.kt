package com.gimlee.ads.domain

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.toMicros
import com.mongodb.client.MongoDatabase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant

class AdminCategoryServiceIntegrationTest(
    private val adminCategoryService: AdminCategoryService,
    private val categoryService: CategoryService,
    private val categoryRepository: CategoryRepository,
    private val adRepository: AdRepository,
    private val mongoDatabase: MongoDatabase
) : BaseIntegrationTest({

    beforeSpec {
        categoryRepository.clear()
        adRepository.clear()
        categoryService.clearCache()
    }

    Given("An empty category tree") {

        When("creating a root GML category") {
            val result = adminCategoryService.createCategory(
                name = mapOf("en-US" to "Electronics", "pl-PL" to "Elektronika"),
                parentId = null
            )

            Then("it should succeed with CATEGORY_CREATED") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_CREATED
                result.data shouldNotBe null
            }
        }

        When("creating a child category under the root") {
            val roots = categoryRepository.findSiblings(null)
            val rootId = roots.first().id

            val result = adminCategoryService.createCategory(
                name = mapOf("en-US" to "Computers", "pl-PL" to "Komputery"),
                parentId = rootId
            )

            Then("it should succeed") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_CREATED
            }
        }

        When("creating a category under a non-existent parent") {
            val result = adminCategoryService.createCategory(
                name = mapOf("en-US" to "Orphan"),
                parentId = 99999
            )

            Then("it should return CATEGORY_INVALID_PARENT") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_INVALID_PARENT
            }
        }
    }

    Given("A category tree with GML categories") {
        categoryRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        val rootId = categoryRepository.getMaxId() + 1
        categoryRepository.insert(rootId, Category.Source(Category.Source.Type.GIMLEE, "gml-$rootId"), null,
            mapOf("en-US" to Category.CategoryName("Animals", "animals")), 0, now)

        val childId = rootId + 1
        categoryRepository.insert(childId, Category.Source(Category.Source.Type.GIMLEE, "gml-$childId"), rootId,
            mapOf("en-US" to Category.CategoryName("Dogs", "dogs")), 0, now)

        val leafId = childId + 1
        categoryRepository.insert(leafId, Category.Source(Category.Source.Type.GIMLEE, "gml-$leafId"), childId,
            mapOf("en-US" to Category.CategoryName("Bulldogs", "bulldogs")), 0, now)

        categoryService.clearCache()

        When("updating category name") {
            val result = adminCategoryService.updateCategory(
                id = rootId,
                name = mapOf("en-US" to "Wild Animals"),
                slug = null,
                hidden = null,
                acknowledge = false
            )

            Then("it should succeed") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_UPDATED
                val updated = categoryRepository.findById(rootId)!!
                updated.name["en-US"]?.name shouldBe "Wild Animals"
            }
        }

        When("deleting a leaf GML category") {
            categoryService.clearCache()
            val result = adminCategoryService.deleteCategory(leafId)

            Then("it should succeed") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_DELETED
                categoryRepository.findById(leafId) shouldBe null
            }
        }

        When("deleting a category with children") {
            categoryService.clearCache()
            val result = adminCategoryService.deleteCategory(rootId)

            Then("it should return CATEGORY_HAS_CHILDREN") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_HAS_CHILDREN
            }
        }
    }

    Given("GPT categories for delete protection") {
        categoryRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        categoryRepository.upsertCategoryBySourceType(
            Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, 1, "gpt-1", null,
            mapOf("en-US" to Category.CategoryName("GPT Root", "gpt-root")), now
        )
        categoryService.clearCache()

        When("attempting to delete a GPT category") {
            val result = adminCategoryService.deleteCategory(1)

            Then("it should return CATEGORY_DELETE_GPT_FORBIDDEN") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_DELETE_GPT_FORBIDDEN
            }
        }
    }

    Given("Categories for reorder testing") {
        categoryRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        val id1 = 100
        val id2 = 101
        val id3 = 102

        categoryRepository.insert(id1, Category.Source(Category.Source.Type.GIMLEE, "gml-$id1"), null,
            mapOf("en-US" to Category.CategoryName("Alpha", "alpha")), 0, now)
        categoryRepository.insert(id2, Category.Source(Category.Source.Type.GIMLEE, "gml-$id2"), null,
            mapOf("en-US" to Category.CategoryName("Beta", "beta")), 1, now)
        categoryRepository.insert(id3, Category.Source(Category.Source.Type.GIMLEE, "gml-$id3"), null,
            mapOf("en-US" to Category.CategoryName("Gamma", "gamma")), 2, now)

        categoryService.clearCache()

        When("reordering Beta up") {
            val result = adminCategoryService.reorderCategory(id2, "UP")

            Then("it should succeed and swap order with Alpha") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_REORDERED
                val alpha = categoryRepository.findById(id1)!!
                val beta = categoryRepository.findById(id2)!!
                beta.displayOrder shouldBe 0
                alpha.displayOrder shouldBe 1
            }
        }

        When("reordering the first category up (boundary)") {
            categoryService.clearCache()
            val siblings = categoryRepository.findSiblings(null).sortedBy { it.displayOrder }
            val firstId = siblings.first().id
            val result = adminCategoryService.reorderCategory(firstId, "UP")

            Then("it should return CATEGORY_ALREADY_AT_BOUNDARY") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_ALREADY_AT_BOUNDARY
            }
        }
    }

    Given("Categories for hide testing with active ads") {
        categoryRepository.clear()
        adRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        val parentId = 200
        val childId = 201
        val leafId = 202

        categoryRepository.insert(parentId, Category.Source(Category.Source.Type.GIMLEE, "gml-$parentId"), null,
            mapOf("en-US" to Category.CategoryName("Parent", "parent")), 0, now)
        categoryRepository.insert(childId, Category.Source(Category.Source.Type.GIMLEE, "gml-$childId"), parentId,
            mapOf("en-US" to Category.CategoryName("Child", "child")), 0, now)
        categoryRepository.insert(leafId, Category.Source(Category.Source.Type.GIMLEE, "gml-$leafId"), childId,
            mapOf("en-US" to Category.CategoryName("Leaf", "leaf")), 0, now)

        // Create an active ad in the leaf category
        val userId = ObjectId.get()
        val adDoc = AdDocument(
            id = ObjectId.get(), userId = userId, title = "Test Ad", description = "desc",
            pricingMode = PricingMode.PEGGED, price = null, currency = null,
            status = AdStatus.ACTIVE,
            createdAtMicros = now, updatedAtMicros = now,
            cityId = null, location = null, mainPhotoPath = null,
            categoryIds = listOf(parentId, childId, leafId),
            stock = 1
        )
        adRepository.save(adDoc)
        categoryService.clearCache()

        When("hiding the parent without acknowledge") {
            val result = adminCategoryService.updateCategory(
                id = parentId, name = null, slug = null, hidden = true, acknowledge = false
            )

            Then("it should return CATEGORY_HAS_ACTIVE_ADS with affected count") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_HAS_ACTIVE_ADS
                @Suppress("UNCHECKED_CAST")
                val data = result.data as Map<String, Any>
                (data["affectedAds"] as Long) shouldBeGreaterThan 0
            }
        }

        When("hiding the parent with acknowledge") {
            categoryService.clearCache()
            val result = adminCategoryService.updateCategory(
                id = parentId, name = null, slug = null, hidden = true, acknowledge = true
            )

            Then("it should succeed and cascade hide") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_HIDDEN

                categoryRepository.findById(parentId)!!.hidden shouldBe true
                categoryRepository.findById(childId)!!.hidden shouldBe true
                categoryRepository.findById(leafId)!!.hidden shouldBe true
            }

            Then("public API should not return hidden categories") {
                categoryService.clearCache()
                val publicRoots = categoryService.getChildren(null, 3, "en-US")
                publicRoots.none { it.id == parentId } shouldBe true
            }
        }

        When("showing the parent (no cascade)") {
            categoryService.clearCache()
            val result = adminCategoryService.updateCategory(
                id = parentId, name = null, slug = null, hidden = false, acknowledge = false
            )

            Then("only the parent should be shown, children remain hidden") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_UPDATED
                categoryRepository.findById(parentId)!!.hidden shouldBe false
                categoryRepository.findById(childId)!!.hidden shouldBe true
                categoryRepository.findById(leafId)!!.hidden shouldBe true
            }
        }
    }

    Given("Categories for move testing") {
        categoryRepository.clear()
        adRepository.clear()
        categoryService.clearCache()
        val now = Instant.now().toMicros()

        val parentAId = 300
        val parentBId = 301
        val movableId = 302

        categoryRepository.insert(parentAId, Category.Source(Category.Source.Type.GIMLEE, "gml-$parentAId"), null,
            mapOf("en-US" to Category.CategoryName("Parent A", "parent-a")), 0, now)
        categoryRepository.insert(parentBId, Category.Source(Category.Source.Type.GIMLEE, "gml-$parentBId"), null,
            mapOf("en-US" to Category.CategoryName("Parent B", "parent-b")), 1, now)
        categoryRepository.insert(movableId, Category.Source(Category.Source.Type.GIMLEE, "gml-$movableId"), parentAId,
            mapOf("en-US" to Category.CategoryName("Movable", "movable")), 0, now)

        categoryService.clearCache()

        When("moving a category to a new parent") {
            val result = adminCategoryService.moveCategory(movableId, parentBId)

            Then("it should succeed") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_MOVED
                val moved = categoryRepository.findById(movableId)!!
                moved.parent shouldBe parentBId
            }
        }

        When("moving a parent under its own child (circular)") {
            categoryService.clearCache()
            val childOfMovable = 303
            categoryRepository.insert(childOfMovable, Category.Source(Category.Source.Type.GIMLEE, "gml-$childOfMovable"), movableId,
                mapOf("en-US" to Category.CategoryName("Sub", "sub")), 0, now)
            categoryService.clearCache()

            val result = adminCategoryService.moveCategory(movableId, childOfMovable)

            Then("it should return CATEGORY_CIRCULAR_PARENT") {
                result.outcome shouldBe CategoryOutcome.CATEGORY_CIRCULAR_PARENT
            }
        }
    }
}) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun setProperties(registry: DynamicPropertyRegistry) {
            registry.add("gimlee.ads.category.sync.enabled") { "false" }
            registry.add("spring.messages.basename") { "i18n/ads/messages" }
        }
    }
}
