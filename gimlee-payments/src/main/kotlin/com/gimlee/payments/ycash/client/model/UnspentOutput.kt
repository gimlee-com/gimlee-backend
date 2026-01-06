package com.gimlee.payments.ycash.client.model

import java.math.BigDecimal

data class UnspentOutput(
    val txid: String,
    val vout: Int,
    val address: String,
    val amount: BigDecimal,
    val spendable: Boolean,
    val confirmations: Int
)