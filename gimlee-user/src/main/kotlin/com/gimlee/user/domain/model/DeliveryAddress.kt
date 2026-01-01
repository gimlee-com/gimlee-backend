package com.gimlee.user.domain.model

import java.time.Instant
import java.util.UUID

data class DeliveryAddress(
    val id: UUID,
    val userId: String,
    val name: String,
    val fullName: String,
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String,
    val phoneNumber: String,
    val isDefault: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
