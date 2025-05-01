package com.gimlee.payments.domain.model

enum class PaymentMethod(
    val id: Int
) {
    PIRATE_CHAIN(0);

    companion object {
        fun lookupById(id: Int): PaymentMethod = entries
            .find { it.id == id }
            ?: throw IllegalArgumentException("Unknown payment method ID: $id")
    }
}