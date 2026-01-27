package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.ads.web.dto.response.CategoryPathElementDto
import com.gimlee.ads.web.dto.response.CategorySuggestionDto
import com.gimlee.ads.web.dto.response.CategoryTreeDto
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val categorySuggester: CategorySuggester
) {

    private val categoriesCache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build<String, List<Category>>()

    private fun getAllCategories(): List<Category> {
        return categoriesCache.get("all") {
            val flatCategories = categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
            val tree = buildCategoryTree(flatCategories)
            categorySuggester.reindex(flatCategories)
            tree
        } ?: emptyList()
    }

    private fun buildCategoryTree(flatCategories: List<Category>): List<Category> {
        val childrenMap = flatCategories.groupBy { it.parent }
        val categoryMap = flatCategories.associateBy { it.id }

        val nodesWithChildren = mutableMapOf<Int, Category>()

        fun getOrCreateNode(id: Int): Category {
            nodesWithChildren[id]?.let { return it }
            val original = categoryMap[id] ?: return Category(
                id = id,
                source = Category.Source(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, "unknown"),
                createdAt = 0,
                updatedAt = 0
            ) // Should not happen with consistent data
            val children = childrenMap[id]?.map { getOrCreateNode(it.id) } ?: emptyList()
            val node = original.copy(children = children)
            nodesWithChildren[id] = node
            return node
        }

        return flatCategories.map { getOrCreateNode(it.id) }
    }

    /**
     * Returns children of a given parent category.
     * If parentId is null, returns root categories.
     * depth parameter controls how many levels of children to return.
     */
    fun getChildren(parentId: Int?, depth: Int, language: String): List<CategoryTreeDto> {
        val allCategories = getAllCategories()
        val targetCategories = if (parentId == null || parentId == 0) {
            allCategories.filter { it.parent == null }
        } else {
            allCategories.find { it.id == parentId }?.children ?: emptyList()
        }

        return targetCategories.map { toTreeDto(it, depth, language) }
    }

    private fun toTreeDto(category: Category, depth: Int, language: String): CategoryTreeDto {
        val nameObj = category.name[language] ?: category.name["en-US"]
        val children = if (depth > 1) {
            category.children.map { toTreeDto(it, depth - 1, language) }
        } else {
            null
        }

        return CategoryTreeDto(
            id = category.id,
            name = nameObj?.name ?: "Unknown",
            slug = nameObj?.slug ?: "",
            hasChildren = category.children.isNotEmpty(),
            children = children
        )
    }

    /**
     * Reconstructs the full category path for a given category ID and language.
     * Falls back to en-US if the requested language is not available.
     */
    fun getFullCategoryPath(categoryId: Int, language: String): List<CategoryPathElementDto>? {
        val categoryMap = getAllCategories().associateBy { it.id }
        return getPath(categoryId, categoryMap, language)
    }

    /**
     * Resolves the full path of category IDs starting from the leaf ID up to the root.
     * Returns the list of integers in order from root to leaf.
     */
    fun resolveCategoryPathIds(leafId: Int): List<Int> {
        val categoryMap = getAllCategories().associateBy { it.id }
        return getPathIds(leafId, categoryMap)
    }

    /**
     * Reconstructs full category paths for a set of category IDs in bulk.
     */
    fun getFullCategoryPaths(categoryIds: Set<Int>, language: String): Map<Int, List<CategoryPathElementDto>> {
        if (categoryIds.isEmpty()) return emptyMap()

        val categoryMap = getAllCategories().associateBy { it.id }
        return categoryIds.associateWith { id ->
            getPath(id, categoryMap, language)
        }.mapNotNull { (id, path) -> if (path != null) id to path else null }.toMap()
    }

    /**
     * Checks if a category is a leaf (i.e., it has no subcategories).
     */
    fun isLeaf(categoryId: Int): Boolean {
        val categories = getAllCategories()
        val categoryExists = categories.any { it.id == categoryId }
        if (!categoryExists) return false

        val parentIds = categories.mapNotNull { it.parent }.toSet()
        return !parentIds.contains(categoryId)
    }

    /**
     * Returns category suggestions based on the search query and language.
     * Includes leaf descendants of matched parent categories.
     */
    fun getSuggestions(query: String, language: String, limit: Int = 10): List<CategorySuggestionDto> {
        val allCategories = getAllCategories()
        val matches = categorySuggester.search(query, language, limit * 2)
        val categoryMap = allCategories.associateBy { it.id }

        val suggestions = mutableListOf<CategorySuggestionDto>()
        val seenIds = mutableSetOf<Int>()

        matches.forEach { match ->
            val category = categoryMap[match.id] ?: return@forEach

            if (category.children.isEmpty()) {
                if (seenIds.add(category.id)) {
                    val path = getPath(category.id, categoryMap, language)
                    if (path != null) {
                        suggestions.add(CategorySuggestionDto(
                            id = category.id,
                            path = path,
                            displayPath = path.joinToString(" > ") { it.name }
                        ))
                    }
                }
            } else {
                val leaves = findLeafDescendants(category)
                leaves.sortedByDescending { it.popularity }.take(5).forEach { leaf ->
                    if (seenIds.add(leaf.id)) {
                        val path = getPath(leaf.id, categoryMap, language)
                        if (path != null) {
                            suggestions.add(CategorySuggestionDto(
                                id = leaf.id,
                                path = path,
                                displayPath = path.joinToString(" > ") { it.name }
                            ))
                        }
                    }
                }
            }
        }

        return suggestions.take(limit)
    }

    private fun findLeafDescendants(category: Category): List<Category> {
        if (category.children.isEmpty()) return listOf(category)
        return category.children.flatMap { findLeafDescendants(it) }
    }

    /**
     * Returns a random leaf category ID. Useful for data population.
     */
    fun getRandomLeafCategoryId(): Int? {
        val categories = getAllCategories()
        val parentIds = categories.mapNotNull { it.parent }.toSet()
        return categories.filter { it.id !in parentIds }.randomOrNull()?.id
    }

    fun clearCache() {
        categoriesCache.invalidateAll()
    }

    private fun getPathIds(id: Int, categoryMap: Map<Int, Category>): List<Int> {
        val category = categoryMap[id] ?: return listOf(id)
        val parentPath = category.parent?.let { getPathIds(it, categoryMap) } ?: emptyList()
        return parentPath + id
    }

    private fun getPath(id: Int, categoryMap: Map<Int, Category>, language: String): List<CategoryPathElementDto>? {
        val category = categoryMap[id] ?: return null
        val name = category.name[language]?.name
            ?: category.name["en-US"]?.name
            ?: return null

        val currentElement = CategoryPathElementDto(id, name)
        val parentPath = category.parent?.let { getPath(it, categoryMap, language) }
        
        return if (parentPath != null) parentPath + currentElement else listOf(currentElement)
    }
}
