package com.gimlee.ads.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * DTO for creating a new ad (initiating it).
 */
data class CreateAdRequestDto(
    @field:NotBlank(message = "Title cannot be blank.")
    @field:Size(max = 100, message = "Title cannot exceed 100 characters.")
    val title: String,

    val categoryId: String? = null
)