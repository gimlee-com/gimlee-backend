package com.gimlee.ads.event

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.events.OrderEvent
import com.gimlee.ads.domain.model.OrderStatus
import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

class OrderEventListenerTest : StringSpec({

    val adRepository = mockk<AdRepository>(relaxed = true)
    val listener = OrderEventListener(adRepository)

    "should increment locked stock on CREATED event" {
        val adId = ObjectId.get()
        val event = OrderEvent(
            orderId = ObjectId.get(),
            adId = adId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = OrderStatus.CREATED.id,
            amount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onOrderEvent(event)

        verify { adRepository.incrementLockedStock(adId) }
    }

    "should complete sale on COMPLETE event" {
        val adId = ObjectId.get()
        val event = OrderEvent(
            orderId = ObjectId.get(),
            adId = adId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = OrderStatus.COMPLETE.id,
            amount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onOrderEvent(event)

        verify { adRepository.completeSale(adId) }
    }

    "should decrement locked stock on FAILED_PAYMENT_TIMEOUT event" {
        val adId = ObjectId.get()
        val event = OrderEvent(
            orderId = ObjectId.get(),
            adId = adId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = OrderStatus.FAILED_PAYMENT_TIMEOUT.id,
            amount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onOrderEvent(event)

        verify { adRepository.decrementLockedStock(adId) }
    }
})
