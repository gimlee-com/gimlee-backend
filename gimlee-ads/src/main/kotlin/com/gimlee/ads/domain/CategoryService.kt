package com.gimlee.ads.domain

import com.github.benmanes.caffeine.cache.Caffeine
import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.ads.web.dto.response.CategoryPathElementDto
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class CategoryService(private val categoryRepository: CategoryRepository) {

    private val categoriesCache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build<String, List<Category>>()

    private fun getAllCategories(): List<Category> {
        return categoriesCache.get("all") { categoryRepository.findAllGptCategories() } ?: emptyList()
    }

    /**
     * Reconstructs the full category path for a given category ID and language.
     * Falls back to en-US if the requested language is not available.
     */
    fun getFullCategoryPath(categoryId: String, language: String): List<CategoryPathElementDto>? {
        val uuid = try {
            UUID.fromString(categoryId)
        } catch (e: Exception) {
            return null
        }
        val categoryMap = getAllCategories().associateBy { it.id }
        return getPath(uuid, categoryMap, language)
    }

    /**
     * Resolves the full path of category IDs starting from the leaf ID up to the root.
     * Returns the list of UUIDs in order from root to leaf.
     */
    fun resolveCategoryPathIds(leafId: UUID): List<UUID> {
        val categoryMap = getAllCategories().associateBy { it.id }
        return getPathIds(leafId, categoryMap)
    }

    /**
     * Reconstructs full category paths for a set of category IDs in bulk.
     */
    fun getFullCategoryPaths(categoryIds: Set<String>, language: String): Map<String, List<CategoryPathElementDto>> {
        if (categoryIds.isEmpty()) return emptyMap()

        val categoryMap = getAllCategories().associateBy { it.id }
        return categoryIds.associateWith { id ->
            try {
                val uuid = UUID.fromString(id)
                getPath(uuid, categoryMap, language)
            } catch (e: Exception) {
                null
            }
        }.mapNotNull { (id, path) -> if (path != null) id to path else null }.toMap()
    }

    /**
     * Checks if a category is a leaf (i.e., it has no subcategories).
     */
    fun isLeaf(categoryId: UUID): Boolean {
        val categories = getAllCategories()
        val categoryExists = categories.any { it.id == categoryId }
        if (!categoryExists) return false

        val parentIds = categories.mapNotNull { it.parent }.toSet()
        return !parentIds.contains(categoryId)
    }

    /**
     * Returns a random leaf category ID. Useful for data population.
     */
    fun getRandomLeafCategoryId(): UUID? {
        val categories = getAllCategories()
        val parentIds = categories.mapNotNull { it.parent }.toSet()
        return categories.filter { it.id !in parentIds }.randomOrNull()?.id
    }

    private fun getPathIds(id: UUID, categoryMap: Map<UUID, Category>): List<UUID> {
        val category = categoryMap[id] ?: return listOf(id)
        val parentPath = category.parent?.let { getPathIds(it, categoryMap) } ?: emptyList()
        return parentPath + id
    }

    private fun getPath(id: UUID, categoryMap: Map<UUID, Category>, language: String): List<CategoryPathElementDto>? {
        val category = categoryMap[id] ?: return null
        val name = category.name[language]?.name
            ?: category.name["en-US"]?.name
            ?: return null

        val currentElement = CategoryPathElementDto(id.toString(), name)
        val parentPath = category.parent?.let { getPath(it, categoryMap, language) }
        
        return if (parentPath != null) parentPath + currentElement else listOf(currentElement)
    }
}
