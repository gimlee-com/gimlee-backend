package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.domain.service.CategorySlugUtils
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.ads.web.dto.response.AdminCategoryDetailDto
import com.gimlee.ads.web.dto.response.AdminCategoryNameDto
import com.gimlee.ads.web.dto.response.AdminCategoryTreeDto
import com.gimlee.ads.web.dto.response.CategoryPathElementDto
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.toMicros
import com.gimlee.events.AdStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AdminCategoryService(
    private val categoryRepository: CategoryRepository,
    private val adRepository: AdRepository,
    private val categoryService: CategoryService,
    private val categorySuggester: CategorySuggester,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class AdminResult(val outcome: Outcome, val data: Any? = null)

    // --- Read Operations ---

    fun getAdminTree(parentId: Int?, depth: Int): List<AdminCategoryTreeDto> {
        val allCategories = getAllCategoriesForAdmin()
        val targetCategories = if (parentId == null || parentId == 0) {
            allCategories.filter { it.parent == null }
        } else {
            allCategories.find { it.id == parentId }?.children ?: emptyList()
        }
        return targetCategories
            .sortedBy { it.displayOrder }
            .map { toAdminTreeDto(it, depth) }
    }

    fun getCategoryDetail(id: Int, language: String): AdminResult {
        val category = categoryRepository.findById(id)
            ?: return AdminResult(CategoryOutcome.CATEGORY_NOT_FOUND)

        val path = categoryService.getFullCategoryPath(id, language)
            ?: listOf(CategoryPathElementDto(id, category.name["en-US"]?.name ?: "Unknown"))

        val detail = AdminCategoryDetailDto(
            id = category.id,
            name = category.name.mapValues { (_, v) -> AdminCategoryNameDto(v.name, v.slug) },
            hasChildren = categoryRepository.countChildren(id) > 0,
            childCount = categoryRepository.countChildren(id),
            parentId = category.parent,
            displayOrder = category.displayOrder,
            hidden = category.hidden,
            sourceType = category.source.type.shortName,
            sourceId = category.source.id,
            adminOverride = category.adminOverride,
            deprecated = category.flags["dep"] ?: false,
            popularity = category.popularity,
            path = path,
            createdAt = category.createdAt,
            updatedAt = category.updatedAt
        )
        return AdminResult(CategoryOutcome.CATEGORY_UPDATED, detail)
    }

    fun searchCategories(query: String, language: String, limit: Int = 20): List<AdminCategoryTreeDto> {
        val matches = categorySuggester.search(query, language, limit, includeHidden = true)
        val matchedIds = matches.map { it.id }.toSet()
        val allCategories = getAllFlatCategoriesForAdmin()
        val categoryMap = allCategories.associateBy { it.id }

        return matches.mapNotNull { match ->
            val category = categoryMap[match.id] ?: return@mapNotNull null
            toAdminTreeDto(category, depth = 1)
        }
    }

    // --- Create ---

    fun createCategory(name: Map<String, String>, parentId: Int?): AdminResult {
        if (parentId != null) {
            val parent = categoryRepository.findById(parentId)
                ?: return AdminResult(CategoryOutcome.CATEGORY_INVALID_PARENT)
            if (parent.hidden) {
                return AdminResult(CategoryOutcome.CATEGORY_INVALID_PARENT)
            }
        }

        val nameMap = buildNameMap(name)
        val id = categoryRepository.getMaxId() + 1
        val displayOrder = categoryRepository.getMaxDisplayOrderForParent(parentId) + 1
        val now = Instant.now().toMicros()
        val sourceId = "gml-$id"

        categoryRepository.insert(
            id = id,
            source = Category.Source(Category.Source.Type.GIMLEE, sourceId),
            parent = parentId,
            nameMap = nameMap,
            displayOrder = displayOrder,
            now = now
        )

        invalidateCaches()
        log.info("Created category {} under parent {}", id, parentId)

        val created = categoryRepository.findById(id)
        return AdminResult(CategoryOutcome.CATEGORY_CREATED, created?.let { toAdminTreeDto(it, 1) })
    }

    // --- Update ---

    fun updateCategory(id: Int, name: Map<String, String>?, slug: Map<String, String>?, hidden: Boolean?, acknowledge: Boolean): AdminResult {
        val category = categoryRepository.findById(id)
            ?: return AdminResult(CategoryOutcome.CATEGORY_NOT_FOUND)

        // Handle hide/show separately from name updates
        if (hidden != null && hidden != category.hidden) {
            if (hidden) {
                return hideCategory(id, acknowledge)
            } else {
                return showCategory(id)
            }
        }

        // Handle name/slug updates
        if (name != null || slug != null) {
            val currentNames = category.name.toMutableMap()

            if (name != null) {
                name.forEach { (lang, newName) ->
                    val currentSlug = slug?.get(lang) ?: CategorySlugUtils.slugify(newName)
                    currentNames[lang] = Category.CategoryName(newName, currentSlug)
                }
            } else if (slug != null) {
                slug.forEach { (lang, newSlug) ->
                    val existing = currentNames[lang] ?: return@forEach
                    currentNames[lang] = existing.copy(slug = newSlug)
                }
            }

            val now = Instant.now().toMicros()
            categoryRepository.updateNameAndSlug(id, currentNames, now)

            if (category.source.type == Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY) {
                categoryRepository.setAdminOverride(id, true)
            }

            invalidateCaches()
            log.info("Updated category {} name/slug", id)
        }

        val updated = categoryRepository.findById(id)
        return AdminResult(CategoryOutcome.CATEGORY_UPDATED, updated?.let { toAdminTreeDto(it, 1) })
    }

    // --- Hide (cascading) ---

    private fun hideCategory(id: Int, acknowledge: Boolean): AdminResult {
        val descendantIds = collectDescendantIds(id)
        val allAffectedIds = listOf(id) + descendantIds
        val activeAdCount = adRepository.countActiveAdsByCategoryIds(allAffectedIds)

        if (activeAdCount > 0 && !acknowledge) {
            return AdminResult(
                CategoryOutcome.CATEGORY_HAS_ACTIVE_ADS,
                mapOf("affectedAds" to activeAdCount)
            )
        }

        // Cascade hide
        categoryRepository.updateHiddenBulk(allAffectedIds, true)

        // Deactivate affected ads
        var deactivatedAds = 0L
        if (activeAdCount > 0) {
            val now = Instant.now().toMicros()
            val affectedAdDocs = adRepository.deactivateAdsByCategoryIds(allAffectedIds, now)
            deactivatedAds = affectedAdDocs.size.toLong()

            affectedAdDocs.forEach { adDoc ->
                eventPublisher.publishEvent(AdStatusChangedEvent(
                    adId = adDoc.id.toHexString(),
                    sellerId = adDoc.userId.toHexString(),
                    oldStatus = "ACTIVE",
                    newStatus = "INACTIVE",
                    categoryIds = adDoc.categoryIds ?: emptyList(),
                    reason = AdStatusChangedEvent.Reason.CATEGORY_HIDDEN
                ))
            }
        }

        invalidateCaches()
        log.info("Hidden category {} and {} descendants, deactivated {} ads", id, descendantIds.size, deactivatedAds)

        return AdminResult(
            CategoryOutcome.CATEGORY_HIDDEN,
            mapOf("hiddenCategories" to allAffectedIds.size, "deactivatedAds" to deactivatedAds)
        )
    }

    // --- Show (single, no cascade) ---

    private fun showCategory(id: Int): AdminResult {
        categoryRepository.updateHidden(id, false)
        invalidateCaches()
        log.info("Showed category {}", id)

        val updated = categoryRepository.findById(id)
        return AdminResult(CategoryOutcome.CATEGORY_UPDATED, updated?.let { toAdminTreeDto(it, 1) })
    }

    // --- Reorder ---

    fun reorderCategory(id: Int, direction: String): AdminResult {
        val category = categoryRepository.findById(id)
            ?: return AdminResult(CategoryOutcome.CATEGORY_NOT_FOUND)

        val siblings = categoryRepository.findSiblings(category.parent)
            .sortedBy { it.displayOrder }
        val currentIndex = siblings.indexOfFirst { it.id == id }
        if (currentIndex == -1) return AdminResult(CategoryOutcome.CATEGORY_NOT_FOUND)

        val targetIndex = when (direction) {
            "UP" -> currentIndex - 1
            "DOWN" -> currentIndex + 1
            else -> return AdminResult(CategoryOutcome.CATEGORY_ALREADY_AT_BOUNDARY)
        }

        if (targetIndex < 0 || targetIndex >= siblings.size) {
            return AdminResult(CategoryOutcome.CATEGORY_ALREADY_AT_BOUNDARY)
        }

        val targetSibling = siblings[targetIndex]

        // Swap display orders
        categoryRepository.updateDisplayOrder(id, targetSibling.displayOrder)
        categoryRepository.updateDisplayOrder(targetSibling.id, category.displayOrder)

        invalidateCaches()
        log.info("Reordered category {} {} (swapped with {})", id, direction, targetSibling.id)

        return AdminResult(CategoryOutcome.CATEGORY_REORDERED)
    }

    // --- Move ---

    fun moveCategory(id: Int, newParentId: Int?): AdminResult {
        val category = categoryRepository.findById(id)
            ?: return AdminResult(CategoryOutcome.CATEGORY_NOT_FOUND)

        if (category.parent == newParentId) {
            return AdminResult(CategoryOutcome.CATEGORY_MOVED)
        }

        if (newParentId != null) {
            if (!categoryRepository.existsById(newParentId)) {
                return AdminResult(CategoryOutcome.CATEGORY_INVALID_PARENT)
            }

            // Circular reference check
            val descendantIds = collectDescendantIds(id)
            if (descendantIds.contains(newParentId) || newParentId == id) {
                return AdminResult(CategoryOutcome.CATEGORY_CIRCULAR_PARENT)
            }
        }

        val newDisplayOrder = categoryRepository.getMaxDisplayOrderForParent(newParentId) + 1
        val now = Instant.now().toMicros()
        categoryRepository.updateParent(id, newParentId, newDisplayOrder, now)

        // Update ads category paths
        updateAdsCategoryPaths(id, category.parent, newParentId)

        invalidateCaches()
        log.info("Moved category {} from parent {} to {}", id, category.parent, newParentId)

        return AdminResult(CategoryOutcome.CATEGORY_MOVED)
    }

    // --- Delete ---

    fun deleteCategory(id: Int): AdminResult {
        val category = categoryRepository.findById(id)
            ?: return AdminResult(CategoryOutcome.CATEGORY_NOT_FOUND)

        if (category.source.type == Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY) {
            return AdminResult(CategoryOutcome.CATEGORY_DELETE_GPT_FORBIDDEN)
        }

        if (categoryRepository.countChildren(id) > 0) {
            return AdminResult(CategoryOutcome.CATEGORY_HAS_CHILDREN)
        }

        val activeAdCount = adRepository.countActiveAdsByCategoryIds(listOf(id))
        if (activeAdCount > 0) {
            return AdminResult(CategoryOutcome.CATEGORY_HAS_ADS)
        }

        categoryRepository.deleteById(id)
        invalidateCaches()
        log.info("Deleted category {}", id)

        return AdminResult(CategoryOutcome.CATEGORY_DELETED)
    }

    // --- Private Helpers ---

    private fun getAllCategoriesForAdmin(): List<Category> {
        val flat = categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY) +
                categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GIMLEE)
        return buildAdminCategoryTree(flat)
    }

    private fun getAllFlatCategoriesForAdmin(): List<Category> {
        return categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY) +
                categoryRepository.findAllCategoriesBySourceType(Category.Source.Type.GIMLEE)
    }

    private fun buildAdminCategoryTree(flatCategories: List<Category>): List<Category> {
        val childrenMap = flatCategories.groupBy { it.parent }
        val categoryMap = flatCategories.associateBy { it.id }
        val nodesWithChildren = mutableMapOf<Int, Category>()

        fun getOrCreateNode(id: Int): Category {
            nodesWithChildren[id]?.let { return it }
            val original = categoryMap[id] ?: return Category(
                id = id,
                source = Category.Source(Category.Source.Type.GIMLEE, "unknown"),
                createdAt = 0,
                updatedAt = 0
            )
            val children = childrenMap[id]
                ?.map { getOrCreateNode(it.id) }
                ?.sortedBy { it.displayOrder }
                ?: emptyList()
            val node = original.copy(children = children)
            nodesWithChildren[id] = node
            return node
        }

        return flatCategories.map { getOrCreateNode(it.id) }
    }

    private fun toAdminTreeDto(category: Category, depth: Int): AdminCategoryTreeDto {
        val children = if (depth > 1) {
            category.children
                .sortedBy { it.displayOrder }
                .map { toAdminTreeDto(it, depth - 1) }
        } else {
            null
        }

        return AdminCategoryTreeDto(
            id = category.id,
            name = category.name.mapValues { (_, v) -> AdminCategoryNameDto(v.name, v.slug) },
            hasChildren = category.children.isNotEmpty(),
            childCount = category.children.size,
            parentId = category.parent,
            displayOrder = category.displayOrder,
            hidden = category.hidden,
            sourceType = category.source.type.shortName,
            popularity = category.popularity,
            createdAt = category.createdAt,
            updatedAt = category.updatedAt,
            children = children
        )
    }

    private fun buildNameMap(name: Map<String, String>): Map<String, Category.CategoryName> {
        val usedSlugs = mutableSetOf<String>()
        return name.mapValues { (_, value) ->
            val originalSlug = CategorySlugUtils.slugify(value)
            var slug = originalSlug
            var counter = 2
            while (usedSlugs.contains(slug)) {
                slug = "$originalSlug-$counter"
                counter++
            }
            usedSlugs.add(slug)
            Category.CategoryName(value, slug)
        }
    }

    private fun collectDescendantIds(categoryId: Int): List<Int> {
        val result = mutableListOf<Int>()
        val allCategories = getAllFlatCategoriesForAdmin()
        collectDescendantIdsFromFlat(categoryId, allCategories, result)
        return result
    }

    private fun collectDescendantIdsFromFlat(parentId: Int, allCategories: List<Category>, result: MutableList<Int>) {
        val children = allCategories.filter { it.parent == parentId }
        children.forEach { child ->
            result.add(child.id)
            collectDescendantIdsFromFlat(child.id, allCategories, result)
        }
    }

    private fun updateAdsCategoryPaths(categoryId: Int, oldParentId: Int?, newParentId: Int?) {
        val oldPath = resolvePath(categoryId, oldParentId)
        val newPath = resolvePath(categoryId, newParentId)
        if (oldPath != newPath) {
            adRepository.updateCategoryPathBulk(oldPath, newPath)
        }
    }

    private fun resolvePath(categoryId: Int, parentId: Int?): List<Int> {
        if (parentId == null) return listOf(categoryId)
        val parentPath = categoryRepository.findById(parentId)?.let { parent ->
            resolvePath(parent.id, parent.parent)
        } ?: emptyList()
        return parentPath + categoryId
    }

    private fun invalidateCaches() {
        categoryService.clearCache()
    }
}
