package com.gimlee.purchases.web.dto.request

import com.gimlee.ads.domain.model.Currency
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class PurchaseRequestDto(
    @field:NotNull(message = "Ad ID is mandatory")
    val adId: String,
    
    @field:NotNull(message = "Amount is mandatory")
    val amount: BigDecimal,

    @field:NotNull(message = "Currency is mandatory")
    val currency: Currency
)
