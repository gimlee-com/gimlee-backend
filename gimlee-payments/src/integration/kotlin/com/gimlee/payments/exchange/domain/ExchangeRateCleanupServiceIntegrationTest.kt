package com.gimlee.payments.exchange.domain

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ExchangeRate
import com.gimlee.payments.persistence.ExchangeRateRepository
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class ExchangeRateCleanupServiceIntegrationTest(
    private val cleanupService: ExchangeRateCleanupService,
    private val repository: ExchangeRateRepository
) : BaseIntegrationTest({

    Given("a set of exchange rates with different update times") {
        repository.clear()
        val now = Instant.now()
        val oldRate = ExchangeRate(
            Currency.ARRR,
            Currency.PLN,
            BigDecimal("0.1"),
            now.minus(366, ChronoUnit.DAYS),
            "OldSource"
        )
        val newRate = ExchangeRate(
            Currency.ARRR,
            Currency.PLN,
            BigDecimal("0.2"),
            now,
            "NewSource"
        )

        repository.save(oldRate)
        repository.save(newRate)

        When("cleanup is executed") {
            val countBefore = repository.count()
            cleanupService.cleanupOldRates()
            val countAfter = repository.count()

            Then("only the new rate should remain") {
                // We use >= and check the difference to be robust against background jobs
                (countBefore - countAfter) shouldBe 1
                val latest = repository.findLatest(Currency.ARRR, Currency.PLN)
                latest?.source shouldBe "NewSource"
            }
        }
    }
})
