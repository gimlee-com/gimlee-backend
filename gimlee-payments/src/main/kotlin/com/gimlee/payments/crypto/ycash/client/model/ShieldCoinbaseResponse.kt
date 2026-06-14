package com.gimlee.payments.crypto.ycash.client.model

import java.math.BigDecimal

data class ShieldCoinbaseResponse(
    val remainingUTXOs: Int,
    val remainingValue: BigDecimal,
    val shieldingUTXOs: Int,
    val shieldingValue: BigDecimal,
    val opid: String
)
