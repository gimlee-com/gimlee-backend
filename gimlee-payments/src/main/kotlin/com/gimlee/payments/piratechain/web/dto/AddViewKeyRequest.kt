package com.gimlee.payments.piratechain.web.dto

import jakarta.validation.constraints.NotEmpty

/**
 * DTO for the request to add a new Pirate Chain viewing key.
 * The corresponding z-address will be derived via RPC interaction.
 */
data class AddViewKeyRequest(
    @field:NotEmpty(message = "Viewing key cannot be empty.")
    val viewKey: String
)