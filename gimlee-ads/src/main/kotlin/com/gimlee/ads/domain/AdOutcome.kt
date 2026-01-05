package com.gimlee.ads.domain

import com.gimlee.common.domain.model.Outcome

enum class AdOutcome(override val httpCode: Int) : Outcome {
    AD_NOT_FOUND(404),
    PIRATE_ROLE_REQUIRED(403),
    INVALID_AD_STATUS(400),
    INVALID_AD_ID(400);

    override val code: String get() = "AD_$name"
    override val messageKey: String get() = "status.ad.${name.replace("_", "-").lowercase()}"
}
