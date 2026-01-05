package com.gimlee.payments.domain

import com.gimlee.common.domain.model.Outcome

enum class PaymentOutcome(override val httpCode: Int) : Outcome {
    NODE_COMMUNICATION_ERROR(503),
    INVALID_PAYMENT_DATA(400),
    PAYMENT_NOT_FOUND(404);

    override val code: String get() = "PAYMENT_$name"
    override val messageKey: String get() = "status.payment.${name.replace("_", "-").lowercase()}"
}
