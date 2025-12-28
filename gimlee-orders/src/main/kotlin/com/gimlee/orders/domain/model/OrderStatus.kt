package com.gimlee.orders.domain.model

enum class OrderStatus(val id: Int) {
    CREATED(0),
    AWAITING_PAYMENT(1),
    COMPLETE(2),
    FAILED_PAYMENT_TIMEOUT(3),
    FAILED_PAYMENT_UNDERPAID(4),
    CANCELLED(5)
}
