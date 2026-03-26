package com.gimlee.api.web.admin

import com.gimlee.api.web.dto.admin.AdminBanDto
import com.gimlee.api.web.dto.admin.AdminUserDetailDto
import com.gimlee.api.web.dto.admin.AdminUserListItemDto
import com.gimlee.api.web.dto.admin.BanUserRequestDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.util.HttpServletRequestAuthUtil
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
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin Users", description = "Admin endpoints for user management")
@RestController
@RequestMapping("/admin/users")
class AdminUserController(
    private val adminUserService: AdminUserService,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "List users",
        description = "Fetches a paginated list of users with optional search and filtering by status."
    )
    @ApiResponse(responseCode = "200", description = "Paginated user list")
    @GetMapping
    @Privileged(role = "ADMIN")
    fun listUsers(
        @Parameter(description = "Search by username or email") @RequestParam(required = false) search: String?,
        @Parameter(description = "Filter by user status") @RequestParam(required = false) status: UserStatus?,
        @Parameter(description = "Sort field (username, lastLogin)") @RequestParam(required = false) sort: String?,
        @Parameter(description = "Sort direction (ASC, DESC)") @RequestParam(required = false, defaultValue = "DESC") direction: String?,
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "30") size: Int
    ): Page<AdminUserListItemDto> {
        return adminUserService.listUsers(search, status, sort, direction, page, size)
    }

    @Operation(
        summary = "Get user details",
        description = "Fetches detailed information about a user including profile, preferences, presence, and statistics."
    )
    @ApiResponse(
        responseCode = "200",
        description = "User details",
        content = [Content(schema = Schema(implementation = AdminUserDetailDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "User not found",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/{userId}")
    @Privileged(role = "ADMIN")
    fun getUserDetail(
        @Parameter(description = "User ID") @PathVariable userId: String
    ): ResponseEntity<Any> {
        val (outcome, data) = adminUserService.getUserDetail(userId)
        return handleOutcome(outcome, data)
    }

    @Operation(
        summary = "Ban user",
        description = "Bans a user with a reason. Optionally set bannedUntil for a temporary ban. Deactivates all active ads."
    )
    @ApiResponse(responseCode = "200", description = "User banned successfully", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "400", description = "Cannot ban admin user", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "User not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "409", description = "User already banned", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PostMapping("/{userId}/ban")
    @Privileged(role = "ADMIN")
    fun banUser(
        @Parameter(description = "User ID") @PathVariable userId: String,
        @Valid @RequestBody request: BanUserRequestDto
    ): ResponseEntity<Any> {
        val adminUserId = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = adminUserService.banUser(userId, request.reason, request.bannedUntil, adminUserId)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Unban user",
        description = "Lifts a ban from a user, restoring their account to active status. Ads are NOT automatically reactivated."
    )
    @ApiResponse(responseCode = "200", description = "User unbanned successfully", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "User not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "409", description = "User is not banned", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PostMapping("/{userId}/unban")
    @Privileged(role = "ADMIN")
    fun unbanUser(
        @Parameter(description = "User ID") @PathVariable userId: String
    ): ResponseEntity<Any> {
        val adminUserId = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = adminUserService.unbanUser(userId, adminUserId)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Get ban history",
        description = "Fetches the full ban history for a user, including past and current bans."
    )
    @ApiResponse(responseCode = "200", description = "List of ban records")
    @ApiResponse(responseCode = "404", description = "User not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @GetMapping("/{userId}/bans")
    @Privileged(role = "ADMIN")
    fun getBanHistory(
        @Parameter(description = "User ID") @PathVariable userId: String
    ): ResponseEntity<Any> {
        val (outcome, data) = adminUserService.getBanHistory(userId)
        return handleOutcome(outcome, data)
    }
}
