package com.gimlee.purchases.domain.model

/**
 * Immutable snapshot of a delivery address captured at the time of purchase.
 */
data class DeliveryAddressSnapshot(
    val name: String,
    val fullName: String,
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String,
    val phoneNumber: String
)
