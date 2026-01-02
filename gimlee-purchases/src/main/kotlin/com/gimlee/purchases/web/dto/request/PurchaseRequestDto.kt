package com.gimlee.purchases.web.dto.request

import com.gimlee.ads.domain.model.Currency
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class PurchaseRequestDto(
    @field:NotEmpty(message = "Items list cannot be empty")
    @field:Valid
    val items: List<PurchaseItemRequestDto>,

    @field:NotNull(message = "Currency is mandatory")
    val currency: Currency
)

data class PurchaseItemRequestDto(
    @field:NotNull(message = "Ad ID is mandatory")
    val adId: String,

    @field:NotNull(message = "Quantity is mandatory")
    @field:Positive(message = "Quantity must be positive")
    val quantity: Int,

    @field:NotNull(message = "Unit price is mandatory")
    val unitPrice: BigDecimal
)
