package com.gimlee.ads.domain

import com.gimlee.common.domain.model.Outcome

enum class AdOutcome(override val httpCode: Int) : Outcome {
    AD_NOT_FOUND(404),
    PIRATE_ROLE_REQUIRED(403),
    YCASH_ROLE_REQUIRED(403),
    INVALID_AD_STATUS(400),
    INVALID_AD_ID(400),
    INVALID_OPERATION(400),
    NOT_AD_OWNER(403),
    TITLE_MANDATORY(400),
    INVALID_MAIN_PHOTO(400),
    INVALID_STOCK(400),
    INCOMPLETE_AD_DATA(400),
    CATEGORY_NOT_LEAF(400),
    CURRENCY_NOT_ALLOWED(400),
    STOCK_LOWER_THAN_LOCKED(400),
    CREATION_FAILED(400),
    UPDATE_FAILED(400),
    ACTIVATION_FAILED(400);

    override val code: String get() = "AD_$name"
    override val messageKey: String get() = "status.ad.${name.replace("_", "-").lowercase()}"
}
