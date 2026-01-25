package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.model.AdDocument
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId

class AdStockServiceTest : StringSpec({

    val adRepository = mockk<AdRepository>(relaxed = true)
    val service = AdStockService(adRepository)

    "should increment locked stock" {
        val adId = ObjectId.get()
        service.incrementLockedStock(adId, 1)
        verify { adRepository.incrementLockedStock(adId, 1) }
    }

    "should decrement locked stock" {
        val adId = ObjectId.get()
        service.decrementLockedStock(adId, 1)
        verify { adRepository.decrementLockedStock(adId, 1) }
    }

    "should commit stock" {
        val adId = ObjectId.get()
        service.commitStock(adId, 1)
        verify { adRepository.decrementStockAndLockedStock(adId, 1) }
    }

    "validateStockLevel should pass if new stock >= locked stock" {
        val adId = ObjectId.get()
        val ad = AdDocument(
            id = adId,
            userId = ObjectId.get(),
            title = "Test",
            description = null,
            price = null,
            currency = null,
            status = AdStatus.ACTIVE,
            createdAtMicros = 0,
            updatedAtMicros = 0,
            cityId = null,
            categoryIds = null,
            location = null,
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = 10,
            lockedStock = 5
        )
        every { adRepository.findById(adId) } returns ad

        service.validateStockLevel(adId, 5)
        service.validateStockLevel(adId, 6)
    }

    "validateStockLevel should fail if new stock < locked stock" {
        val adId = ObjectId.get()
        val ad = AdDocument(
            id = adId,
            userId = ObjectId.get(),
            title = "Test",
            description = null,
            price = null,
            currency = null,
            status = AdStatus.ACTIVE,
            createdAtMicros = 0,
            updatedAtMicros = 0,
            cityId = null,
            categoryIds = null,
            location = null,
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = 10,
            lockedStock = 5
        )
        every { adRepository.findById(adId) } returns ad

        val exception = shouldThrow<AdService.AdOperationException> {
            service.validateStockLevel(adId, 4)
        }
        exception.outcome shouldBe AdOutcome.STOCK_LOWER_THAN_LOCKED
        exception.args shouldBe arrayOf(4, 5)
    }
})
