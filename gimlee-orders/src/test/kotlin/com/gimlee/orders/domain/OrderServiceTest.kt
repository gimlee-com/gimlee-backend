package com.gimlee.orders.domain

import com.gimlee.events.OrderEvent
import com.gimlee.orders.domain.model.OrderStatus
import com.gimlee.events.PaymentEvent
import com.gimlee.orders.domain.model.Order
import com.gimlee.orders.persistence.OrderRepository
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import io.kotest.core.spec.style.StringSpec
import io.mockk.*
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import java.math.BigDecimal
import java.time.Instant

class OrderServiceTest : StringSpec({

    val orderRepository = mockk<OrderRepository>(relaxed = true)
    val paymentService = mockk<PaymentService>(relaxed = true)
    val adService = mockk<AdService>(relaxed = true)
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = OrderService(orderRepository, paymentService, adService, eventPublisher)

    "should init order and payment and publish events" {
        val buyerId = ObjectId.get()
        val sellerId = ObjectId.get()
        val adId = ObjectId.get()
        val amount = BigDecimal("10.0")

        val orderSlot = slot<Order>()
        every { orderRepository.save(capture(orderSlot)) } answers { orderSlot.captured }

        service.initOrder(buyerId, sellerId, adId, amount)

        verify { paymentService.initPayment(any(), buyerId, sellerId, amount, PaymentMethod.PIRATE_CHAIN) }
        verify(exactly = 2) { orderRepository.save(any()) } 
        verify(exactly = 2) { eventPublisher.publishEvent(any<OrderEvent>()) }
    }

    "should place order successfully if stock available" {
        val buyerId = ObjectId.get()
        val adId = ObjectId.get()
        val sellerId = ObjectId.get()
        val ad = Ad(
            id = adId.toHexString(),
            userId = sellerId.toHexString(),
            title = "Test Ad",
            description = "Desc",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAd(adId.toHexString()) } returns ad
        
        service.placeOrder(buyerId, adId, BigDecimal.TEN, Currency.ARRR)

        verify { orderRepository.save(match { it.status == OrderStatus.CREATED }) }
    }

    "should throw AdPriceMismatchException if price mismatch" {
        val buyerId = ObjectId.get()
        val adId = ObjectId.get()
        val sellerId = ObjectId.get()
        val ad = Ad(
            id = adId.toHexString(),
            userId = sellerId.toHexString(),
            title = "Test Ad",
            description = "Desc",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAd(adId.toHexString()) } returns ad
        
        io.kotest.assertions.throwables.shouldThrow<OrderService.AdPriceMismatchException> {
            service.placeOrder(buyerId, adId, BigDecimal.ONE, Currency.ARRR)
        }
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
