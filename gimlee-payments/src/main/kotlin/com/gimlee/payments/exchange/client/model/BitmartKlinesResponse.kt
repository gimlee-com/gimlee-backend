package com.gimlee.payments.exchange.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitmartKlinesResponse(
    val code: Int,
    val message: String,
    val data: List<List<String>>?
)
