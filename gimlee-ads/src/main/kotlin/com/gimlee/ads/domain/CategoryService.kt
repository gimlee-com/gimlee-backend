package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.ads.web.dto.response.CategoryPathElementDto
import org.springframework.stereotype.Service
import java.util.*

@Service
class CategoryService(private val categoryRepository: CategoryRepository) {

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
        val categoryMap = categoryRepository.findAllGptCategories().associateBy { it.id }
        return getPath(uuid, categoryMap, language)
    }

    /**
     * Reconstructs full category paths for a set of category IDs in bulk.
     */
    fun getFullCategoryPaths(categoryIds: Set<String>, language: String): Map<String, List<CategoryPathElementDto>> {
        if (categoryIds.isEmpty()) return emptyMap()

        val categoryMap = categoryRepository.findAllGptCategories().associateBy { it.id }
        return categoryIds.associateWith { id ->
            try {
                val uuid = UUID.fromString(id)
                getPath(uuid, categoryMap, language)
            } catch (e: Exception) {
                null
            }
        }.mapNotNull { (id, path) -> if (path != null) id to path else null }.toMap()
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
