package com.gimlee.user.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "gimlee.user.preferences")
data class UserPreferencesProperties(
    @DefaultValue("USD")
    val defaultCurrency: String = "USD",
    val currencyMappings: Map<String, String> = emptyMap()
)
