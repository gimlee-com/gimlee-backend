package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad
import java.math.BigDecimal

data class AdPreviewDto(
    val id: String,
    val title: String,
    val price: BigDecimal? = null,
    val mainPhotoPath: String? = null
) {
    companion object {
        fun fromAd(ad: Ad) = AdPreviewDto(
            id = ad.id,
            title = ad.title,
            price = ad.price,
            mainPhotoPath = ad.mainPhotoPath
        )
    }
}