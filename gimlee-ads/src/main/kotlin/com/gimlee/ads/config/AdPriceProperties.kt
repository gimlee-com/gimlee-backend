package com.gimlee.ads.config

import com.gimlee.common.domain.model.Currency
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
@ConfigurationProperties(prefix = "gimlee.ads.max-price")
data class AdPriceProperties(
    var amount: BigDecimal = BigDecimal("10000"),
    var currency: Currency = Currency.USD
)
