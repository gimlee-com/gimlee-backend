package com.gimlee.ads.event

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.events.PurchaseEvent
import com.gimlee.ads.domain.model.PurchaseStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

class PurchaseEventListenerTest : StringSpec({

    val adRepository = mockk<AdRepository>(relaxed = true)
    val listener = PurchaseEventListener(adRepository)

    "should increment locked stock on CREATED event" {
        val adId = ObjectId.get()
        val event = PurchaseEvent(
            purchaseId = ObjectId.get(),
            adId = adId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = PurchaseStatus.CREATED.id,
            amount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onPurchaseEvent(event)

        verify { adRepository.incrementLockedStock(adId) }
    }

    "should complete sale on COMPLETE event" {
        val adId = ObjectId.get()
        val event = PurchaseEvent(
            purchaseId = ObjectId.get(),
            adId = adId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = PurchaseStatus.COMPLETE.id,
            amount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onPurchaseEvent(event)

        verify { adRepository.decrementStockAndLockedStock(adId) }
    }

    "should decrement locked stock on FAILED_PAYMENT_TIMEOUT event" {
        val adId = ObjectId.get()
        val event = PurchaseEvent(
            purchaseId = ObjectId.get(),
            adId = adId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = PurchaseStatus.FAILED_PAYMENT_TIMEOUT.id,
            amount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        listener.onPurchaseEvent(event)

        verify { adRepository.decrementLockedStock(adId) }
    }

    "should propagate exception when incrementLockedStock fails" {
        val adId = ObjectId.get()
        val event = PurchaseEvent(
            purchaseId = ObjectId.get(),
            adId = adId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            status = PurchaseStatus.CREATED.id,
            amount = BigDecimal.TEN,
            timestamp = Instant.now()
        )

        every { adRepository.incrementLockedStock(adId) } throws IllegalStateException("Cannot lock more stock")

        shouldThrow<IllegalStateException> {
            listener.onPurchaseEvent(event)
        }
    }
})
