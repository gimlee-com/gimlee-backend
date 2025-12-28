package com.gimlee.orders.domain

import com.gimlee.events.PaymentEvent
import com.gimlee.orders.domain.model.Order
import com.gimlee.orders.domain.model.OrderStatus
import com.gimlee.orders.persistence.OrderRepository
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

class OrderServiceTest : StringSpec({

    val orderRepository = mockk<OrderRepository>(relaxed = true)
    val paymentService = mockk<PaymentService>(relaxed = true)
    val service = OrderService(orderRepository, paymentService)

    "should init order and payment" {
        val buyerId = ObjectId.get()
        val sellerId = ObjectId.get()
        val adId = ObjectId.get()
        val amount = BigDecimal("10.0")

        val orderSlot = slot<Order>()
        every { orderRepository.save(capture(orderSlot)) } answers { orderSlot.captured }

        service.initOrder(buyerId, sellerId, adId, amount)

        verify { paymentService.initPayment(any(), buyerId, sellerId, amount, PaymentMethod.PIRATE_CHAIN) }
        verify(exactly = 2) { orderRepository.save(any()) } // Once created, once status update
    }

    "should update order status on payment complete event" {
        val orderId = ObjectId.get()
        val order = Order(
            id = orderId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            adId = ObjectId.get(),
            amount = BigDecimal("10.0"),
            status = OrderStatus.AWAITING_PAYMENT,
            createdAt = Instant.now()
        )

        every { orderRepository.findById(orderId) } returns order

        val event = PaymentEvent(
            orderId = orderId,
            buyerId = order.buyerId,
            sellerId = order.sellerId,
            status = PaymentStatus.COMPLETE.id,
            paymentMethod = PaymentMethod.PIRATE_CHAIN.id,
            amount = order.amount,
            timestamp = Instant.now()
        )

        service.onPaymentEvent(event)

        verify { orderRepository.save(match { it.status == OrderStatus.COMPLETE }) }
    }
})
