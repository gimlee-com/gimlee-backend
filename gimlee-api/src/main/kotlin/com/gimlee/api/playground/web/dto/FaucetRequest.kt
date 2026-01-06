package com.gimlee.api.playground.web.dto

import java.math.BigDecimal

data class FaucetRequest(
    val address: String,
    val amount: BigDecimal = BigDecimal("0.1")
)
