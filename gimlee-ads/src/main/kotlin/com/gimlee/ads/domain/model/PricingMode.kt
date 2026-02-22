package com.gimlee.ads.domain.model

enum class PricingMode(val shortName: String) {
    FIXED_CRYPTO("FC"),
    PEGGED("PG");

    companion object {
        fun fromShortName(shortName: String): PricingMode =
            entries.first { it.shortName == shortName }
    }
}
