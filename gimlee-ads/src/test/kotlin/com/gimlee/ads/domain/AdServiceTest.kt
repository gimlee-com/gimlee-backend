package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.service.CurrencyConverterService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal

class AdServiceTest : StringSpec({

    val adRepository = mockk<AdRepository>()
    val adStockService = mockk<AdStockService>(relaxed = true)
    val categoryService = mockk<CategoryService>(relaxed = true)
    val currencyConverterService = mockk<CurrencyConverterService>(relaxed = true)
    val userRoleRepository = mockk<UserRoleRepository>(relaxed = true)
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val adCurrencyService = AdCurrencyService()
    val adCurrencyValidator = AdCurrencyValidator(adCurrencyService)
    val adService = AdService(adRepository, adStockService, categoryService, currencyConverterService, adCurrencyValidator, adCurrencyService, userRoleRepository, eventPublisher)

    "updateAd should fail if using ARRR without PIRATE role" {
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
            stock = 10
        )

        every { adRepository.findById(adId) } returns existingDoc
        every { userRoleRepository.getAll(userId) } returns listOf(Role.USER) // No PIRATE role

        val updateRequest = com.gimlee.ads.domain.model.UpdateAdRequest(
            price = com.gimlee.ads.domain.model.CurrencyAmount(BigDecimal("100"), Currency.ARRR)
        )

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdCurrencyRoleException> {
            adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)
        }

        exception.outcome shouldBe AdOutcome.PIRATE_ROLE_REQUIRED
    }

    "updateAd should succeed if using ARRR with PIRATE role" {
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
            stock = 10
        )

        every { adRepository.findById(adId) } returns existingDoc
        every { userRoleRepository.getAll(userId) } returns listOf(Role.USER, Role.PIRATE)
        every { adRepository.save(any()) } answers { it.invocation.args[0] as AdDocument }

        val updateRequest = com.gimlee.ads.domain.model.UpdateAdRequest(
            price = com.gimlee.ads.domain.model.CurrencyAmount(BigDecimal("100"), Currency.ARRR)
        )

        val result = adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)
        result.price?.currency shouldBe Currency.ARRR
    }

    "updateAd should fail if using YEC without YCASH role" {
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
            stock = 10
        )

        every { adRepository.findById(adId) } returns existingDoc
        every { userRoleRepository.getAll(userId) } returns listOf(Role.USER) // No YCASH role

        val updateRequest = com.gimlee.ads.domain.model.UpdateAdRequest(
            price = com.gimlee.ads.domain.model.CurrencyAmount(BigDecimal("100"), Currency.YEC)
        )

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdCurrencyRoleException> {
            adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)
        }

        exception.outcome shouldBe AdOutcome.YCASH_ROLE_REQUIRED
    }

    "updateAd should succeed if using YEC with YCASH role" {
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
            stock = 10
        )

        every { adRepository.findById(adId) } returns existingDoc
        every { userRoleRepository.getAll(userId) } returns listOf(Role.USER, Role.YCASH)
        every { adRepository.save(any()) } answers { it.invocation.args[0] as AdDocument }

        val updateRequest = com.gimlee.ads.domain.model.UpdateAdRequest(
            price = com.gimlee.ads.domain.model.CurrencyAmount(BigDecimal("100"), Currency.YEC)
        )

        val result = adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)
        result.price?.currency shouldBe Currency.YEC
    }

    "updateAd should fail if using non-settlement currency" {
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
            stock = 10
        )

        every { adRepository.findById(adId) } returns existingDoc

        val updateRequest = com.gimlee.ads.domain.model.UpdateAdRequest(
            price = com.gimlee.ads.domain.model.CurrencyAmount(BigDecimal("100"), Currency.USD)
        )

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)
        }

        exception.outcome shouldBe AdOutcome.CURRENCY_NOT_ALLOWED
        exception.args shouldBe arrayOf("USD")
    }

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
        every { userRoleRepository.getAll(userId) } returns listOf(Role.USER, Role.PIRATE)

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.activateAd(adId.toHexString(), userId.toHexString())
        }

        exception.outcome shouldBe AdOutcome.INCOMPLETE_AD_DATA
    }

    "activateAd should fail if currency is not a settlement one" {
        val adId = ObjectId()
        val userId = ObjectId()
        val existingDoc = AdDocument(
            id = adId,
            userId = userId,
            title = "Test Ad",
            description = "Description",
            price = BigDecimal("100"),
            currency = Currency.USD,
            status = AdStatus.INACTIVE,
            createdAtMicros = 1000L,
            updatedAtMicros = 1000L,
            cityId = "city1",
            categoryIds = null,
            location = org.springframework.data.mongodb.core.geo.GeoJsonPoint(1.0, 2.0),
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = 10
        )

        every { adRepository.findById(adId) } returns existingDoc
        every { userRoleRepository.getAll(userId) } returns listOf(Role.USER)

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.activateAd(adId.toHexString(), userId.toHexString())
        }

        exception.outcome shouldBe AdOutcome.CURRENCY_NOT_ALLOWED
        exception.args shouldBe arrayOf("USD")
    }

    "updateAd should fail if stock level is lower than locked stock" {
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
        every { adStockService.validateStockLevel(adId, 4) } throws AdService.AdOperationException(AdOutcome.STOCK_LOWER_THAN_LOCKED, 4, 5)

        val updateRequest = UpdateAdRequest(stock = 4)

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.updateAd(adId.toHexString(), userId.toHexString(), updateRequest)
        }

        exception.outcome shouldBe AdOutcome.STOCK_LOWER_THAN_LOCKED
        exception.args shouldBe arrayOf(4, 5)
    }

    "createAd should fail if category is not a leaf" {
        val userId = ObjectId().toHexString()
        val title = "Test Ad"
        val categoryId = 123

        every { categoryService.isLeaf(any()) } returns false

        val exception = io.kotest.assertions.throwables.shouldThrow<AdService.AdOperationException> {
            adService.createAd(userId, title, categoryId)
        }

        exception.outcome shouldBe AdOutcome.CATEGORY_NOT_LEAF
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

        exception.outcome shouldBe AdOutcome.CATEGORY_NOT_LEAF
    }
})
