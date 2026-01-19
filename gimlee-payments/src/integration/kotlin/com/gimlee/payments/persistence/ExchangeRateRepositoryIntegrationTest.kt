package com.gimlee.payments.persistence

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ExchangeRate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.time.Instant

class ExchangeRateRepositoryIntegrationTest(
    private val repository: ExchangeRateRepository
) : BaseIntegrationTest({
    
    Given("a set of exchange rates for the same pair") {
        repository.clear()
        val now = Instant.now()
        val rate1 = ExchangeRate(Currency.YEC, Currency.USDT, BigDecimal("1.5"), now.minusSeconds(60), "Source1")
        val rate2 = ExchangeRate(Currency.YEC, Currency.USDT, BigDecimal("1.6"), now, "Source2")

        When("they are saved to the repository") {
            repository.save(rate1)
            repository.save(rate2)

            Then("finding the latest should return the most recent one") {
                val latest = repository.findLatest(Currency.YEC, Currency.USDT)
                latest shouldNotBe null
                latest?.rate?.stripTrailingZeros() shouldBe BigDecimal("1.6").stripTrailingZeros()
                latest?.source shouldBe "Source2"
            }
        }
    }

    Given("exchange rates for multiple pairs") {
        repository.clear()
        val now = Instant.now()
        val rate1 = ExchangeRate(Currency.YEC, Currency.USDT, BigDecimal("1.5"), now, "Source")
        val rate2 = ExchangeRate(Currency.ARRR, Currency.USDT, BigDecimal("0.2"), now, "Source")
        val rate3 = ExchangeRate(Currency.YEC, Currency.USDT, BigDecimal("1.4"), now.minusSeconds(60), "OldSource")

        When("they are saved") {
            repository.save(rate1)
            repository.save(rate2)
            repository.save(rate3)

            Then("findAllLatest should return only the latest rate for each unique pair") {
                val allLatest = repository.findAllLatest()
                allLatest.size shouldBe 2
                allLatest.any { it.baseCurrency == Currency.YEC && it.rate.stripTrailingZeros() == BigDecimal("1.5").stripTrailingZeros() } shouldBe true
                allLatest.any { it.baseCurrency == Currency.ARRR && it.rate.stripTrailingZeros() == BigDecimal("0.2").stripTrailingZeros() } shouldBe true
            }
        }
    }
})
