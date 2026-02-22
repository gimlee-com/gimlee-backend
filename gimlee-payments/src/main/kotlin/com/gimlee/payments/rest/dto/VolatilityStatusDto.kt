package com.gimlee.payments.rest.dto

import java.math.BigDecimal
import java.time.Instant

data class VolatilityStatusDto(
    val currency: String,
    val isVolatile: Boolean,
    val startTime: Instant?,
    val cooldownEndsAt: Instant?,
    val currentDropPct: BigDecimal?,
    val maxPriceInWindow: BigDecimal?,
    val isStale: Boolean = false
)
