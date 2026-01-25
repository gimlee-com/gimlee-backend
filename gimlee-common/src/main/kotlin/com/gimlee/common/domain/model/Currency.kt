package com.gimlee.common.domain.model

enum class CurrencyType {
    FIAT, CRYPTO
}

/**
 * Represents the supported currencies for ad pricing and payments.
 */
enum class Currency(val type: CurrencyType, val decimalPlaces: Int, val isSettlement: Boolean, val requiredRole: String? = null) {
    /** Pirate Chain */
    ARRR(CurrencyType.CRYPTO, 8, true, "PIRATE"),

    /** YCash */
    YEC(CurrencyType.CRYPTO, 8, true, "YCASH"),

    /** Tether */
    USDT(CurrencyType.CRYPTO, 8, false),

    /** US Dollar */
    USD(CurrencyType.FIAT, 2, false),

    /** Polish Zloty */
    PLN(CurrencyType.FIAT, 2, false),

    /** Gold (Troy Ounce) */
    XAU(CurrencyType.FIAT, 6, false);
}
