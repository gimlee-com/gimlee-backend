package com.gimlee.orders.domain

import com.gimlee.events.PaymentEvent
import com.gimlee.orders.domain.model.Order
import com.gimlee.orders.domain.model.OrderStatus
import com.gimlee.orders.persistence.OrderRepository
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val paymentService: PaymentService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun initOrder(
        buyerId: ObjectId,
        sellerId: ObjectId,
        adId: ObjectId,
        amount: BigDecimal
    ): Order {
        val orderId = ObjectId.get()
        val now = Instant.now()
        
        val order = Order(
            id = orderId,
            buyerId = buyerId,
            sellerId = sellerId,
            adId = adId,
            amount = amount,
            status = OrderStatus.CREATED,
            createdAt = now
        )
        
        orderRepository.save(order)

        // Initialize payment
        paymentService.initPayment(
            orderId = orderId,
            buyerId = buyerId,
            sellerId = sellerId,
            amount = amount,
            paymentMethod = PaymentMethod.PIRATE_CHAIN
        )

        // Update status to AWAITING_PAYMENT
        val activeOrder = order.copy(status = OrderStatus.AWAITING_PAYMENT)
        return orderRepository.save(activeOrder)
    }
    
    @EventListener
    fun onPaymentEvent(event: PaymentEvent) {
        val order = orderRepository.findById(event.orderId) ?: return
        
        val newStatus = when (event.status) {
            PaymentStatus.COMPLETE.id -> OrderStatus.COMPLETE
            PaymentStatus.COMPLETE_UNDERPAID.id -> OrderStatus.FAILED_PAYMENT_UNDERPAID
            PaymentStatus.FAILED_SOFT_TIMEOUT.id -> OrderStatus.FAILED_PAYMENT_TIMEOUT
            PaymentStatus.FAILED_HARD_TIMEOUT.id -> OrderStatus.FAILED_PAYMENT_TIMEOUT
            PaymentStatus.CANCELLED.id -> OrderStatus.CANCELLED
            else -> null
        }

        if (newStatus != null && order.status != newStatus) {
            log.info("Updating order ${order.id} status to $newStatus based on payment event.")
            orderRepository.save(order.copy(status = newStatus))
        }
    }
    
    fun getOrder(orderId: ObjectId): Order? = orderRepository.findById(orderId)
}
