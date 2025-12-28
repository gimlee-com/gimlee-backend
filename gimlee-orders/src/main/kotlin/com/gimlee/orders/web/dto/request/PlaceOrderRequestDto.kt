package com.gimlee.orders.web.dto.request

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class PlaceOrderRequestDto(
    @field:NotNull(message = "Ad ID is mandatory")
    val adId: String,
    
    @field:NotNull(message = "Amount is mandatory")
    val amount: BigDecimal
)
