package com.gimlee.purchases.domain

import com.gimlee.common.domain.model.Outcome

enum class PurchaseOutcome(override val httpCode: Int) : Outcome {
    PRICE_MISMATCH(409),
    PURCHASE_NOT_FOUND(404),
    NOT_A_PARTICIPANT(403),
    INVALID_PURCHASE_REQUEST(400),
    ORDER_NOT_FOUND(404),
    CANNOT_PURCHASE_FROM_SELF(400),
    ADS_NOT_FOUND(400),
    ADS_NOT_ACTIVE(400),
    STOCK_INSUFFICIENT(400),
    DELIVERY_ADDRESS_NOT_FOUND(404),
    DELIVERY_ADDRESS_COUNTRY_MISMATCH(400),
    COUNTRY_OF_RESIDENCE_REQUIRED(400);

    override val code: String get() = "PURCHASE_$name"
    override val messageKey: String get() = "status.purchase.${name.replace("_", "-").lowercase()}"
}
