package com.gimlee.common.domain.model

enum class CurrencyType {
    FIAT, CRYPTO
}

/**
 * Represents the supported currencies for ad pricing and payments.
 */
enum class Currency(val type: CurrencyType) {
    /** Pirate Chain */
    ARRR(CurrencyType.CRYPTO),

    /** YCash */
    YEC(CurrencyType.CRYPTO);
}
