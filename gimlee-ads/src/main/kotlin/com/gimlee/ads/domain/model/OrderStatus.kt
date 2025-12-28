package com.gimlee.ads.domain.model

enum class OrderStatus(val id: Int) {
    CREATED(0),
    COMPLETE(2),
    FAILED_PAYMENT_TIMEOUT(3),
    FAILED_PAYMENT_UNDERPAID(4),
    CANCELLED(5)
}
