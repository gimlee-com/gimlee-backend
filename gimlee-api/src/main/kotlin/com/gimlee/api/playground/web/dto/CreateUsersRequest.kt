package com.gimlee.api.playground.web.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request to create playground users")
data class CreateUsersRequest(
    @Schema(description = "Optional PirateChain view key to register for the 'playground_seller' user")
    val pirateViewKey: String? = null,
    @Schema(description = "Optional YCash view key to register for the 'playground_seller' user")
    val ycashViewKey: String? = null
)
