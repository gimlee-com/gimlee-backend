package com.gimlee.ads.web

import com.gimlee.ads.domain.CategoryService
import com.gimlee.ads.web.dto.response.CategoryTreeDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Categories", description = "Endpoints for category traversal and lookup")
@RestController
@RequestMapping("/ads/categories")
class CategoryController(private val categoryService: CategoryService) {

    @Operation(
        summary = "Get Category Roots",
        description = "Fetches the root level categories for marketplace navigation."
    )
    @ApiResponse(responseCode = "200", description = "List of root categories")
    @GetMapping
    fun getRoots(
        @Parameter(description = "Depth of the tree to return. Default is 1 (only immediate roots).")
        @RequestParam(defaultValue = "1") depth: Int
    ): List<CategoryTreeDto> {
        return categoryService.getChildren(null, depth, LocaleContextHolder.getLocale().toLanguageTag())
    }

    @Operation(
        summary = "Get Category Children",
        description = "Fetches the subcategories of a given category to allow deeper traversal."
    )
    @ApiResponse(responseCode = "200", description = "List of subcategories")
    @GetMapping("/{categoryId}/children")
    fun getChildren(
        @Parameter(description = "ID of the parent category")
        @PathVariable categoryId: Int,
        @Parameter(description = "Depth of the tree to return. Default is 1 (only immediate children).")
        @RequestParam(defaultValue = "1") depth: Int
    ): List<CategoryTreeDto> {
        return categoryService.getChildren(categoryId, depth, LocaleContextHolder.getLocale().toLanguageTag())
    }
}
