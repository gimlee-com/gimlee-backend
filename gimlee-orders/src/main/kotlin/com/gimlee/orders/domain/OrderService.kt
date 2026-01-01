package com.gimlee.orders.domain

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.events.OrderEvent
import com.gimlee.orders.domain.model.OrderStatus
import com.gimlee.events.PaymentEvent
import com.gimlee.orders.domain.model.Order
import com.gimlee.orders.persistence.OrderRepository
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val paymentService: PaymentService,
    private val adService: AdService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun placeOrder(
        buyerId: ObjectId,
        adId: ObjectId,
        amount: BigDecimal,
        currency: Currency
    ): Order {
        val ad = adService.getAd(adId.toHexString())
            ?: throw IllegalArgumentException("Ad not found: $adId")

        val adPrice = ad.price
        if (adPrice == null) {
            throw IllegalStateException("Ad $adId has no price set.")
        }

        if (adPrice.currency != currency || adPrice.amount.compareTo(amount) != 0) {
            log.warn("Order placement rejected for ad {}: Price mismatch. Requested: {} {}, Actual: {} {}",
                adId, amount, currency, adPrice.amount, adPrice.currency)
            throw AdPriceMismatchException(adPrice)
        }

        val availableStock = ad.stock - ad.lockedStock
        if (availableStock <= 0) {
            throw IllegalStateException("Ad $adId has no available stock.")
        }

        return initOrder(
            buyerId = buyerId,
            sellerId = ObjectId(ad.userId),
            adId = adId,
            amount = amount
        )
    }

    class AdPriceMismatchException(val currentPrice: CurrencyAmount) : RuntimeException("Price has changed.")

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
        publishOrderEvent(order)

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
        val savedOrder = orderRepository.save(activeOrder)
        publishOrderEvent(savedOrder)

        return savedOrder
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
            val updatedOrder = order.copy(status = newStatus)
            orderRepository.save(updatedOrder)
            publishOrderEvent(updatedOrder)
        }
    }

    private fun publishOrderEvent(order: Order) {
        val event = OrderEvent(
            orderId = order.id,
            adId = order.adId,
            buyerId = order.buyerId,
            sellerId = order.sellerId,
            status = order.status.id,
            amount = order.amount,
            timestamp = Instant.now()
        )
        eventPublisher.publishEvent(event)
    }
    
    fun getOrder(orderId: ObjectId): Order? = orderRepository.findById(orderId)
}
