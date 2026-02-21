package com.gimlee.api.web.session

import com.gimlee.ads.config.AdPriceProperties
import com.gimlee.api.web.dto.InitSessionResponseDto
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(5)
class AdConfigDecorator(
    private val adPriceProperties: AdPriceProperties
) : SessionDecorator {

    override val name: String = "adConfig"

    override fun decorate(response: InitSessionResponseDto, request: HttpServletRequest) {
        response.data["adConfig"] = mapOf(
            "maxPrice" to mapOf(
                "amount" to adPriceProperties.amount,
                "currency" to adPriceProperties.currency
            )
        )
    }
}
