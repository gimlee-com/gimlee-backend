package com.gimlee.ads.domain

import com.gimlee.ads.config.AdPriceProperties
import com.gimlee.ads.domain.AdService.AdOperationException
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ConversionResult
import com.gimlee.payments.domain.service.CurrencyConverterService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant

class AdPriceValidatorTest : StringSpec({

    val properties = mockk<AdPriceProperties>()
    val currencyConverterService = mockk<CurrencyConverterService>()
    val validator = AdPriceValidator(properties, currencyConverterService)

    val limitAmount = BigDecimal("10000")
    val limitCurrency = Currency.USD

    beforeTest {
        every { properties.amount } returns limitAmount
        every { properties.currency } returns limitCurrency
    }

    "should allow price below limit" {
        val newPrice = CurrencyAmount(BigDecimal("5000"), Currency.USD)
        
        every { currencyConverterService.convert(BigDecimal("5000"), Currency.USD, Currency.USD) } returns 
            ConversionResult(BigDecimal("5000"), Currency.USD, Currency.USD, emptyList(), Instant.now(), false)

        validator.validatePrice(newPrice, null)
    }

    "should reject price above limit for new ad" {
        val newPrice = CurrencyAmount(BigDecimal("15000"), Currency.USD)
        
        every { currencyConverterService.convert(BigDecimal("15000"), Currency.USD, Currency.USD) } returns 
            ConversionResult(BigDecimal("15000"), Currency.USD, Currency.USD, emptyList(), Instant.now(), false)

        val exception = shouldThrow<AdOperationException> {
            validator.validatePrice(newPrice, null)
        }
        exception.outcome shouldBe AdOutcome.PRICE_EXCEEDS_LIMIT
    }

    "should allow price above limit if grandfathered (reduced price)" {
        val newPrice = CurrencyAmount(BigDecimal("12000"), Currency.USD)
        val oldPrice = CurrencyAmount(BigDecimal("13000"), Currency.USD)
        
        every { currencyConverterService.convert(BigDecimal("12000"), Currency.USD, Currency.USD) } returns 
            ConversionResult(BigDecimal("12000"), Currency.USD, Currency.USD, emptyList(), Instant.now(), false)

        validator.validatePrice(newPrice, oldPrice)
    }

    "should reject price above limit if increased" {
        val newPrice = CurrencyAmount(BigDecimal("14000"), Currency.USD)
        val oldPrice = CurrencyAmount(BigDecimal("13000"), Currency.USD)
        
        every { currencyConverterService.convert(BigDecimal("14000"), Currency.USD, Currency.USD) } returns 
            ConversionResult(BigDecimal("14000"), Currency.USD, Currency.USD, emptyList(), Instant.now(), false)

        val exception = shouldThrow<AdOperationException> {
            validator.validatePrice(newPrice, oldPrice)
        }
        exception.outcome shouldBe AdOutcome.PRICE_EXCEEDS_LIMIT
    }

    "should reject price above limit if currency changed" {
        val newPrice = CurrencyAmount(BigDecimal("40000"), Currency.PLN) // Converted > 10000 USD (assuming ~4 PLN/USD)
        val oldPrice = CurrencyAmount(BigDecimal("14000"), Currency.USD)
        
        every { currencyConverterService.convert(BigDecimal("40000"), Currency.PLN, Currency.USD) } returns 
            ConversionResult(BigDecimal("10001"), Currency.PLN, Currency.USD, emptyList(), Instant.now(), false)

        val exception = shouldThrow<AdOperationException> {
            validator.validatePrice(newPrice, oldPrice)
        }
        exception.outcome shouldBe AdOutcome.PRICE_EXCEEDS_LIMIT
    }
})
