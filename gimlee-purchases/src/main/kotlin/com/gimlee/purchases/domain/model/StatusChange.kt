package com.gimlee.purchases.domain.model

import java.time.Instant

data class StatusChange(
    val status: PurchaseStatus,
    val timestamp: Instant
)
