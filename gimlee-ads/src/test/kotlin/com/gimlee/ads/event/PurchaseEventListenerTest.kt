package com.gimlee.ads.event

import com.gimlee.ads.domain.AdStockService
import com.gimlee.events.PurchaseEvent
import com.gimlee.ads.domain.model.PurchaseStatus
import com.gimlee.events.PurchaseEventItem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

class PurchaseEventListenerTest : StringSpec({

    val adStockService = mockk<AdStockService>(relaxed = true)
    val listener = PurchaseEventListener(adStockService)

    "should increment locked stock on CREATED event" {
        val adId = ObjectId.get()
        val event = PurchaseEvent(
            purchaseId = ObjectId.get(),
            items = listOf(PurchaseEventItem(adId, 1)),
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = PurchaseStatus.CREATED.id,
            totalAmount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onPurchaseEvent(event)

        verify { adStockService.incrementLockedStock(adId, 1) }
    }

    "should complete sale on COMPLETE event" {
        val adId = ObjectId.get()
        val event = PurchaseEvent(
            purchaseId = ObjectId.get(),
            items = listOf(PurchaseEventItem(adId, 2)),
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = PurchaseStatus.COMPLETE.id,
            totalAmount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onPurchaseEvent(event)

        verify { adStockService.commitStock(adId, 2) }
    }

    "should decrement locked stock on FAILED_PAYMENT_TIMEOUT event" {
        val adId = ObjectId.get()
        val event = PurchaseEvent(
            purchaseId = ObjectId.get(),
            items = listOf(PurchaseEventItem(adId, 1)),
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = PurchaseStatus.FAILED_PAYMENT_TIMEOUT.id,
            totalAmount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onPurchaseEvent(event)

        verify { adStockService.decrementLockedStock(adId, 1) }
    }

    "should propagate exception when incrementLockedStock fails" {
        val adId = ObjectId.get()
        val event = PurchaseEvent(
            purchaseId = ObjectId.get(),
            items = listOf(PurchaseEventItem(adId, 1)),
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = PurchaseStatus.CREATED.id,
            totalAmount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        every { adStockService.incrementLockedStock(adId, 1) } throws IllegalStateException("Cannot lock more stock")

        shouldThrow<IllegalStateException> {
            listener.onPurchaseEvent(event)
        }
    }
})
