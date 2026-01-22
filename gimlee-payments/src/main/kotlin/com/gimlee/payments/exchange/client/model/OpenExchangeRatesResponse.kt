package com.gimlee.payments.exchange.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenExchangeRatesResponse(
    val timestamp: Long,
    val base: String,
    val rates: Map<String, BigDecimal>
)
