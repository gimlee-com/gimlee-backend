package com.gimlee.payments.domain.service

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ExchangeRate
import com.gimlee.payments.persistence.ExchangeRateRepository
import com.gimlee.payments.config.PaymentProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant

class CurrencyConverterServiceTest : StringSpec({

    isolationMode = IsolationMode.InstancePerTest

    val repository = mockk<ExchangeRateRepository>()
    val properties = PaymentProperties(timeoutHours = 1)
    val service = CurrencyConverterService(repository, properties)

    "should convert directly if from and to are the same" {
        val amount = BigDecimal("100")
        val result = service.convert(amount, Currency.USD, Currency.USD)
        result.targetAmount.stripTrailingZeros() shouldBe amount.stripTrailingZeros()
        result.targetAmount.scale() shouldBe 2
        result.steps.size shouldBe 0
    }

    "should find a single hop path" {
        val amount = BigDecimal("2")
        val rate = ExchangeRate(Currency.YEC, Currency.USDT, BigDecimal("1.5"), Instant.now(), "Source")
        every { repository.findAllLatest() } returns listOf(rate)

        val result = service.convert(amount, Currency.YEC, Currency.USDT)
        result.targetAmount.stripTrailingZeros() shouldBe BigDecimal("3").stripTrailingZeros()
        result.targetAmount.scale() shouldBe 8
        result.steps.size shouldBe 1
    }

    "should find a multi hop path" {
        val amount = BigDecimal("2")
        val rate1 = ExchangeRate(Currency.YEC, Currency.USDT, BigDecimal("1.5"), Instant.now(), "Source1")
        val rate2 = ExchangeRate(Currency.USDT, Currency.USD, BigDecimal("1.0"), Instant.now(), "Source2")
        val rate3 = ExchangeRate(Currency.USD, Currency.PLN, BigDecimal("4.0"), Instant.now(), "Source3")
        every { repository.findAllLatest() } returns listOf(rate1, rate2, rate3)

        val result = service.convert(amount, Currency.YEC, Currency.PLN)
        result.targetAmount.stripTrailingZeros() shouldBe BigDecimal("12").stripTrailingZeros()
        result.targetAmount.scale() shouldBe 2
        result.steps.size shouldBe 3
    }

    "should use inverse rate if needed" {
        val amount = BigDecimal("12")
        val rate = ExchangeRate(Currency.YEC, Currency.PLN, BigDecimal("4.0"), Instant.now(), "Source")
        every { repository.findAllLatest() } returns listOf(rate)

        val result = service.convert(amount, Currency.PLN, Currency.YEC)
        result.targetAmount.stripTrailingZeros() shouldBe BigDecimal("3").stripTrailingZeros()
        result.targetAmount.scale() shouldBe 8
        result.steps.size shouldBe 1
        result.steps[0].rate.stripTrailingZeros() shouldBe BigDecimal("0.25") // 1 / 4.0
    }

    "should throw exception if no path found" {
        every { repository.findAllLatest() } returns emptyList()
        shouldThrow<RuntimeException> {
            service.convert(BigDecimal.ONE, Currency.YEC, Currency.PLN)
        }
    }

    "should convert YEC to XAU" {
        // YEC -> USDT (1.5)
        // USDT -> USD (1.0)
        // USD -> XAU (0.0005)
        // Total: 1 * 1.5 * 1.0 * 0.0005 = 0.00075
        val amount = BigDecimal("1")
        val rate1 = ExchangeRate(Currency.YEC, Currency.USDT, BigDecimal("1.5"), Instant.now(), "Source1")
        val rate2 = ExchangeRate(Currency.USDT, Currency.USD, BigDecimal("1.0"), Instant.now(), "Source2")
        val rate3 = ExchangeRate(Currency.USD, Currency.XAU, BigDecimal("0.0005"), Instant.now(), "Source3")
        every { repository.findAllLatest() } returns listOf(rate1, rate2, rate3)

        val result = service.convert(amount, Currency.YEC, Currency.XAU)
        result.targetAmount.stripTrailingZeros() shouldBe BigDecimal("0.00075").stripTrailingZeros()
        result.targetAmount.scale() shouldBe 6
        result.steps.size shouldBe 3
    }
})
