package com.gimlee.payments.crypto.piratechain.client.model

import java.math.BigDecimal

data class ZSendManyAmount(
    val address: String,
    val amount: BigDecimal,
    val memo: String? = null
)
