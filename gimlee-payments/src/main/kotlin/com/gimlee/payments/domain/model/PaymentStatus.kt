package com.gimlee.payments.domain.model

enum class PaymentStatus(val id: Int) {
    CREATED(0),
    AWAITING_CONFIRMATION(1),
    COMPLETE(2),
    COMPLETE_OVERPAID(3),
    COMPLETE_UNDERPAID(4),
    FAILED_SOFT_TIMEOUT(5),
    FAILED_HARD_TIMEOUT(6),
    CANCELLED(7)
}