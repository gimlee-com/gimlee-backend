package com.gimlee.common.domain.model

enum class CurrencyType {
    FIAT, CRYPTO
}

/**
 * Represents the supported currencies for ad pricing and payments.
 */
enum class Currency(val type: CurrencyType, val decimalPlaces: Int) {
    /** Pirate Chain */
    ARRR(CurrencyType.CRYPTO, 8),

    /** YCash */
    YEC(CurrencyType.CRYPTO, 8),

    /** Tether */
    USDT(CurrencyType.CRYPTO, 8),

    /** US Dollar */
    USD(CurrencyType.FIAT, 2),

    /** Polish Zloty */
    PLN(CurrencyType.FIAT, 2),

    /** Gold (Troy Ounce) */
    XAU(CurrencyType.FIAT, 6);
}
