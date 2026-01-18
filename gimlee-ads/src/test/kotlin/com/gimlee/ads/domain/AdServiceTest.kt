package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.common.domain.model.Currency
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import java.math.BigDecimal

class AdServiceTest : StringSpec({

    val adRepository = mockk<AdRepository>()
    val adStockService = mockk<AdStockService>(relaxed = true)
    val categoryService = mockk<CategoryService>(relaxed = true)
    val adService = AdService(adRepository, adStockService, categoryService)

    "createAd should set stock" {
        val userId = ObjectId().toHexString()
        val title = "Test Ad"
        val stock = 10

        every { adRepository.save(any()) } answers { it.invocation.args[0] as AdDocument }

        val result = adService.createAd(userId, title, null, stock)

        result.title shouldBe title
        result.stock shouldBe stock
        verify {
            adRepository.save(match {
                it.title == title && it.stock == stock
            })
        }
    }

    "updateAd should update stock" {
        val adId = ObjectId()
        val userId = ObjectId()
        val existingDoc = AdDocument(
            id = adId,
            userId = userId,
            title = "Old Title",
            description = null,
            price = null,
            currency = null,
            status = AdStatus.INACTIVE,
            createdAtMicros = 1000L,
            updatedAtMicros = 1000L,
            cityId = null,
            categoryIds = null,
            location = null,
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = 5
        )

        every { adRepository.findById(adId) } returns existingDoc
        every { adRepository.save(any()) } answers { it.invocation.args[0] as AdDocument }

        val updateRequest = UpdateAdRequest(
            title = null,
            description = null,
            price = null,
            location = null,
            mediaPaths = null,
            mainPhotoPath = null,
            stock = 20
        )

        val result = adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)

        result.stock shouldBe 20
        verify {
            adRepository.save(match {
                it.id == adId && it.stock == 20
            })
        }
    }

    "activateAd should fail if stock is 0" {
        val adId = ObjectId()
        val userId = ObjectId()
        val existingDoc = AdDocument(
            id = adId,
            userId = userId,
            title = "Test Ad",
            description = "Description",
            price = BigDecimal("100"),
            currency = Currency.ARRR,
            status = AdStatus.INACTIVE,
            createdAtMicros = 1000L,
            updatedAtMicros = 1000L,
            cityId = "city1",
            categoryIds = null,
            location = org.springframework.data.mongodb.core.geo.GeoJsonPoint(1.0, 2.0),
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = 0
        )

        every { adRepository.findById(adId) } returns existingDoc

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.activateAd(adId.toHexString(), userId.toHexString())
        }

        exception.message shouldBe "Ad cannot be activated until title, description, price, location and stock are all set. Stock must be greater than 0."
    }

    "updateAd should fail if repository throws IllegalStateException (stock constraint)" {
        val adId = ObjectId()
        val userId = ObjectId()
        val existingDoc = AdDocument(
            id = adId,
            userId = userId,
            title = "Title",
            description = null,
            price = null,
            currency = null,
            status = AdStatus.INACTIVE,
            createdAtMicros = 1000L,
            updatedAtMicros = 1000L,
            cityId = null,
            categoryIds = null,
            location = null,
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = 10,
            lockedStock = 5
        )

        every { adRepository.findById(adId) } returns existingDoc
        every { adRepository.save(any()) } throws IllegalStateException("Stock (4) cannot be lower than locked stock (5).")

        val updateRequest = UpdateAdRequest(stock = 4)

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)
        }

        exception.message shouldBe "Stock (4) cannot be lower than locked stock (5)."
    }

    "createAd should fail if category is not a leaf" {
        val userId = ObjectId().toHexString()
        val title = "Test Ad"
        val categoryId = 123

        every { categoryService.isLeaf(any()) } returns false

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.createAd(userId, title, categoryId)
        }

        exception.message shouldBe "Category must be a leaf node."
    }

    "updateAd should fail if category is not a leaf" {
        val adId = ObjectId()
        val userId = ObjectId()
        val categoryId = 123
        val existingDoc = AdDocument(
            id = adId,
            userId = userId,
            title = "Title",
            description = null,
            price = null,
            currency = null,
            status = AdStatus.INACTIVE,
            createdAtMicros = 1000L,
            updatedAtMicros = 1000L,
            cityId = null,
            categoryIds = null,
            location = null,
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = 10
        )

        every { adRepository.findById(adId) } returns existingDoc
        every { categoryService.isLeaf(any()) } returns false

        val updateRequest = UpdateAdRequest(categoryId = categoryId)

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)
        }

        exception.message shouldBe "Category must be a leaf node."
    }
})
