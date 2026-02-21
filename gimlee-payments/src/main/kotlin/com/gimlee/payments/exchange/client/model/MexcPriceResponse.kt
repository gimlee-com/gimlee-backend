package com.gimlee.payments.exchange.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class MexcPriceResponse(
    val symbol: String,
    val price: String
)
