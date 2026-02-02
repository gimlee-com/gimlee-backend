package com.gimlee.api.web

import com.gimlee.api.web.dto.InitSessionResponseDto
import com.gimlee.api.web.dto.SessionInitResponseDocumentationDto
import com.gimlee.api.web.session.SessionDecorator
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Session", description = "Endpoints for session initialization and configuration")
@RestController
@RequestMapping("/session")
class SessionInitController(
    private val decorators: List<SessionDecorator>
) {
    @Operation(
        summary = "Initialize Session",
        description = """
            Initializes the front-end session and returns requested data decorators.
            Each decorator adds specific fields to the root of the response object.
            
            Available decorators:
            * `accessToken`: Adds `accessToken` (String?) with the current JWT token.
            * `userProfile`: Adds `userProfile` (UserProfileDto?) with the user's profile details.
            * `preferredCurrency`: Adds `preferredCurrency` (String?) with the user's preferred currency (e.g., USD, PLN).
            * `publicChatId`: Adds `publicChatId` (String?) with the hardcoded public chat ID.
        """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Session data returned",
        content = [Content(schema = Schema(implementation = SessionInitResponseDocumentationDto::class))]
    )
    @GetMapping("/init")
    fun init(
        @Parameter(
            description = "List of decorators to include in the response",
            array = ArraySchema(schema = Schema(allowableValues = ["accessToken", "userProfile", "preferredCurrency", "publicChatId"]))
        )
        @RequestParam(required = false) decorators: List<String>?,
        request: HttpServletRequest
    ): InitSessionResponseDto {
        val requestedDecorators = decorators ?: emptyList()
        val response = InitSessionResponseDto()

        this.decorators
            .filter { requestedDecorators.contains(it.name) }
            .forEach { it.decorate(response, request) }

        return response
    }
}
