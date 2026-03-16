package com.gimlee.ads.web

import com.gimlee.ads.domain.AdminCategoryService
import com.gimlee.ads.web.dto.request.CreateCategoryRequestDto
import com.gimlee.ads.web.dto.request.MoveCategoryRequestDto
import com.gimlee.ads.web.dto.request.ReorderCategoryRequestDto
import com.gimlee.ads.web.dto.request.UpdateCategoryRequestDto
import com.gimlee.ads.web.dto.response.AdminCategoryDetailDto
import com.gimlee.ads.web.dto.response.AdminCategoryTreeDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin Categories", description = "Admin endpoints for managing the category tree")
@RestController
@RequestMapping("/admin/categories")
class AdminCategoryController(
    private val adminCategoryService: AdminCategoryService,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "List root categories",
        description = "Fetches root-level categories with admin-specific fields including hidden status and display order."
    )
    @ApiResponse(responseCode = "200", description = "List of root categories")
    @GetMapping
    @Privileged(role = "ADMIN")
    fun getRoots(
        @Parameter(description = "Depth of the tree to return. Default is 1.")
        @RequestParam(defaultValue = "1") depth: Int
    ): List<AdminCategoryTreeDto> {
        return adminCategoryService.getAdminTree(null, depth)
    }

    @Operation(
        summary = "Get category details",
        description = "Fetches detailed information about a single category including source info and admin flags."
    )
    @ApiResponse(responseCode = "200", description = "Category details", content = [Content(schema = Schema(implementation = AdminCategoryDetailDto::class))])
    @ApiResponse(responseCode = "404", description = "Category not found")
    @GetMapping("/{id}")
    @Privileged(role = "ADMIN")
    fun getCategory(
        @Parameter(description = "Category ID")
        @PathVariable id: Int
    ): ResponseEntity<Any> {
        val result = adminCategoryService.getCategoryDetail(id, LocaleContextHolder.getLocale().toLanguageTag())
        return handleOutcome(result.outcome, result.data)
    }

    @Operation(
        summary = "Get category children",
        description = "Fetches the subcategories of a given category with admin-specific fields."
    )
    @ApiResponse(responseCode = "200", description = "List of subcategories")
    @GetMapping("/{id}/children")
    @Privileged(role = "ADMIN")
    fun getChildren(
        @Parameter(description = "Parent category ID")
        @PathVariable id: Int,
        @Parameter(description = "Depth of the tree to return. Default is 1.")
        @RequestParam(defaultValue = "1") depth: Int
    ): List<AdminCategoryTreeDto> {
        return adminCategoryService.getAdminTree(id, depth)
    }

    @Operation(
        summary = "Search categories",
        description = "Searches categories by name, including hidden categories. Results include path information."
    )
    @ApiResponse(responseCode = "200", description = "List of matching categories")
    @GetMapping("/search")
    @Privileged(role = "ADMIN")
    fun searchCategories(
        @Parameter(description = "Search query")
        @RequestParam("q") query: String,
        @Parameter(description = "Maximum number of results")
        @RequestParam(defaultValue = "20") limit: Int
    ): List<AdminCategoryTreeDto> {
        return adminCategoryService.searchCategories(query, LocaleContextHolder.getLocale().toLanguageTag(), limit)
    }

    @Operation(
        summary = "Create category",
        description = "Creates a new GIMLEE category. Slugs are auto-generated from names."
    )
    @ApiResponse(responseCode = "201", description = "Category created")
    @ApiResponse(responseCode = "400", description = "Invalid parent category")
    @PostMapping
    @Privileged(role = "ADMIN")
    fun createCategory(
        @Valid @RequestBody request: CreateCategoryRequestDto
    ): ResponseEntity<Any> {
        val result = adminCategoryService.createCategory(request.name, request.parentId)
        return handleOutcome(result.outcome, result.data)
    }

    @Operation(
        summary = "Update category",
        description = "Updates a category's name, slug, or visibility. When hiding a category with active ads, set acknowledge=true to confirm deactivation."
    )
    @ApiResponse(responseCode = "200", description = "Category updated")
    @ApiResponse(responseCode = "404", description = "Category not found")
    @ApiResponse(responseCode = "409", description = "Category has active ads that would be deactivated (requires acknowledge)")
    @PatchMapping("/{id}")
    @Privileged(role = "ADMIN")
    fun updateCategory(
        @Parameter(description = "Category ID")
        @PathVariable id: Int,
        @Valid @RequestBody request: UpdateCategoryRequestDto
    ): ResponseEntity<Any> {
        val result = adminCategoryService.updateCategory(id, request.name, request.slug, request.hidden, request.acknowledge)
        return handleOutcome(result.outcome, result.data)
    }

    @Operation(
        summary = "Reorder category",
        description = "Moves a category up or down among its siblings."
    )
    @ApiResponse(responseCode = "200", description = "Category reordered")
    @ApiResponse(responseCode = "400", description = "Category is already at the boundary")
    @ApiResponse(responseCode = "404", description = "Category not found")
    @PatchMapping("/{id}/reorder")
    @Privileged(role = "ADMIN")
    fun reorderCategory(
        @Parameter(description = "Category ID")
        @PathVariable id: Int,
        @Valid @RequestBody request: ReorderCategoryRequestDto
    ): ResponseEntity<Any> {
        val result = adminCategoryService.reorderCategory(id, request.direction)
        return handleOutcome(result.outcome, result.data)
    }

    @Operation(
        summary = "Move category",
        description = "Moves a category to a new parent. Set newParentId to null to move to root. Updates category paths in all affected ads."
    )
    @ApiResponse(responseCode = "200", description = "Category moved")
    @ApiResponse(responseCode = "400", description = "Invalid parent or circular reference")
    @ApiResponse(responseCode = "404", description = "Category not found")
    @PatchMapping("/{id}/move")
    @Privileged(role = "ADMIN")
    fun moveCategory(
        @Parameter(description = "Category ID")
        @PathVariable id: Int,
        @Valid @RequestBody request: MoveCategoryRequestDto
    ): ResponseEntity<Any> {
        val result = adminCategoryService.moveCategory(id, request.newParentId)
        return handleOutcome(result.outcome, result.data)
    }

    @Operation(
        summary = "Delete category",
        description = "Deletes a GIMLEE category. GPT categories cannot be deleted (use hide instead). Category must have no children and no ads."
    )
    @ApiResponse(responseCode = "200", description = "Category deleted")
    @ApiResponse(responseCode = "400", description = "Cannot delete GPT category")
    @ApiResponse(responseCode = "404", description = "Category not found")
    @ApiResponse(responseCode = "409", description = "Category has children or ads")
    @DeleteMapping("/{id}")
    @Privileged(role = "ADMIN")
    fun deleteCategory(
        @Parameter(description = "Category ID")
        @PathVariable id: Int
    ): ResponseEntity<Any> {
        val result = adminCategoryService.deleteCategory(id)
        return handleOutcome(result.outcome, result.data)
    }
}
