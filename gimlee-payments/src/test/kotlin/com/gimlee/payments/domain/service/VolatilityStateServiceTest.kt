package com.gimlee.payments.domain.service

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.config.VolatilityProperties
import com.gimlee.payments.domain.model.ExchangeRate
import com.gimlee.payments.persistence.ExchangeRateRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant

class VolatilityStateServiceTest : BehaviorSpec({
    val exchangeRateRepository = mockk<ExchangeRateRepository>()
    val paymentProperties = PaymentProperties(
        timeoutHours = 1,
        volatility = VolatilityProperties(
            downsideThreshold = 0.05,
            windowSeconds = 600,
            cooldownSeconds = 1800,
            stabilizationChecks = 3,
            staleThresholdSeconds = 3600
        )
    )
    val service = VolatilityStateService(paymentProperties, exchangeRateRepository)

    fun rate(currency: Currency, amount: BigDecimal, time: Instant = Instant.now()): ExchangeRate {
        return ExchangeRate(
            baseCurrency = currency,
            quoteCurrency = Currency.USDT,
            rate = amount,
            updatedAt = time,
            source = "TEST"
        )
    }

    Given("A stable market") {
        // Mock findLatest to return a fresh rate
        every { exchangeRateRepository.findLatest(any(), any()) } returns rate(Currency.ARRR, BigDecimal("10.0"), Instant.now())
        
        every { exchangeRateRepository.findRatesInWindow(any(), any(), any(), any()) } returns listOf(
            rate(Currency.ARRR, BigDecimal("10.0")),
            rate(Currency.ARRR, BigDecimal("10.1")),
            rate(Currency.ARRR, BigDecimal("9.9"))
        )

        When("Update volatility states") {
            service.updateVolatilityStates()

            Then("ARRR should not be volatile") {
                service.isVolatile(Currency.ARRR) shouldBe false
            }
            Then("ARRR should not be frozen") {
                service.isFrozen(Currency.ARRR) shouldBe false
            }
        }
    }

    Given("A crashing market (Drop > 5%)") {
        // Mock findLatest to return a fresh rate
        every { exchangeRateRepository.findLatest(any(), any()) } returns rate(Currency.ARRR, BigDecimal("9.4"), Instant.now())
        
        // Max 10.0, Current 9.4 -> Drop 6%
        every { exchangeRateRepository.findRatesInWindow(any(), any(), any(), any()) } returns listOf(
            rate(Currency.ARRR, BigDecimal("9.4"), Instant.now()), // Latest
            rate(Currency.ARRR, BigDecimal("9.8"), Instant.now().minusSeconds(100)),
            rate(Currency.ARRR, BigDecimal("10.0"), Instant.now().minusSeconds(200)) // Max
        )

        When("Update volatility states") {
            service.updateVolatilityStates()

            Then("ARRR should be volatile") {
                service.isVolatile(Currency.ARRR) shouldBe true
            }
            Then("ARRR should be frozen") {
                service.isFrozen(Currency.ARRR) shouldBe true
            }
            
            Then("Volatility state should capture drop") {
                val state = service.getVolatilityState(Currency.ARRR)
                state.isVolatile shouldBe true
                state.maxPriceInWindow shouldBe BigDecimal("10.0")
                // 0.0600
                state.currentDropPct?.setScale(2) shouldBe BigDecimal("0.06")
            }
        }
    }

    Given("Market recovers but inside cooldown") {
        // Mock findLatest to return a fresh rate
        every { exchangeRateRepository.findLatest(any(), any()) } returns rate(Currency.ARRR, BigDecimal("9.9"), Instant.now())
        
        // Drop < 5% (Recovered price)
        every { exchangeRateRepository.findRatesInWindow(any(), any(), any(), any()) } returns listOf(
            rate(Currency.ARRR, BigDecimal("9.9")), // Latest (recovering)
            rate(Currency.ARRR, BigDecimal("10.0")) // Max
        )
        // 1% drop

        // Mock time if necessary, but here we rely on service using Instant.now()
        // We can't easily mock Instant.now() inside the service without a Clock bean.
        // For this test, since the service was just triggered in previous step,
        // cooldown (1800s) has not passed.
        
        When("Update volatility states immediately after crash") {
            service.updateVolatilityStates()

            Then("ARRR should STILL be volatile (Cooldown)") {
                service.isVolatile(Currency.ARRR) shouldBe true
            }
        }
    }
    
    Given("Stale market data (Older than 1h)") {
        val oldTime = Instant.now().minusSeconds(4000) // > 3600s
        every { exchangeRateRepository.findLatest(any(), any()) } returns rate(Currency.ARRR, BigDecimal("10.0"), oldTime)
        
        When("Update volatility states with stale data") {
            service.updateVolatilityStates()
            
            Then("ARRR should be STALE and FROZEN") {
                val state = service.getVolatilityState(Currency.ARRR)
                state.isStale shouldBe true
                service.isFrozen(Currency.ARRR) shouldBe true
            }
        }
    }

    Given("Market becomes fresh after being stale but has no rates in current window") {
        val oldTime = Instant.now().minusSeconds(4000)
        every { exchangeRateRepository.findLatest(any(), any()) } returns rate(Currency.ARRR, BigDecimal("10.0"), oldTime)
        service.updateVolatilityStates()

        every { exchangeRateRepository.findLatest(any(), any()) } returns rate(Currency.ARRR, BigDecimal("10.0"), Instant.now())
        every { exchangeRateRepository.findRatesInWindow(any(), any(), any(), any()) } returns emptyList()

        When("Update volatility states with fresh latest rate") {
            service.updateVolatilityStates()

            Then("ARRR stale flag should clear immediately") {
                val state = service.getVolatilityState(Currency.ARRR)
                state.isStale shouldBe false
            }
        }
    }
})
