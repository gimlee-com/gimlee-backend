package com.gimlee.payments.crypto.piratechain.client.model

import java.math.BigDecimal

data class ShieldedUnspentOutput(
    val txid: String,
    val outindex: Int? = null,
    val confirmations: Int,
    val spendable: Boolean,
    val address: String,
    val amount: BigDecimal,
    val memo: String? = null,
    val change: Boolean
)
