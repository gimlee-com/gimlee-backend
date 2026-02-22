package com.gimlee.ads.web.dto.request

import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.common.domain.model.Currency
import com.gimlee.location.cities.data.cityDataById
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import java.math.BigDecimal


data class UpdateAdRequestDto(
    @field:Size(max = 100, message = "Title cannot exceed 100 characters.")
    val title: String?,

    @field:Size(max = 5000, message = "Description cannot exceed 5000 characters.")
    val description: String?,

    val pricingMode: PricingMode?,

    @field:DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive.")
    val price: BigDecimal?,

    val priceCurrency: Currency?,

    val settlementCurrencies: Set<Currency>?,

    val categoryId: Int?,

    @field:Valid
    val location: LocationDto?,

    @field:Size(max = 20, message = "Cannot have more than 20 media items.")
    val mediaPaths: List<@Size(max = 255, message = "Media path too long.") String>?,

    @field:Size(max = 255, message = "Main photo path too long.")
    val mainPhotoPath: String?,

    val stock: Int?,
    
    val volatilityProtection: Boolean?
) {
    fun toDomain(): UpdateAdRequest {
        return UpdateAdRequest(
            title = title,
            description = description,
            pricingMode = pricingMode,
            price = price?.let {
                require(priceCurrency != null) { "Price currency must be provided when the price is provided." }
                CurrencyAmount(price, priceCurrency)
            },
            settlementCurrencies = settlementCurrencies,
            location = location?.let { dto ->
                val point = dto.point ?: cityDataById[dto.cityId]?.let { doubleArrayOf(it.lon, it.lat) }
                requireNotNull(point) { "Location point is mandatory and city ID '${dto.cityId}' is invalid." }
                Location(cityId = dto.cityId, point = point)
            },
            categoryId = categoryId,
            mediaPaths = mediaPaths,
            mainPhotoPath = mainPhotoPath,
            stock = stock,
            volatilityProtection = volatilityProtection
        )
    }
}