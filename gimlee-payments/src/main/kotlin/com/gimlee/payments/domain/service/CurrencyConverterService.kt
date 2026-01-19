package com.gimlee.payments.domain.service

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ConversionException
import com.gimlee.payments.domain.model.ConversionResult
import com.gimlee.payments.domain.model.ConversionStep
import com.gimlee.payments.domain.model.ExchangeRate
import com.gimlee.payments.persistence.ExchangeRateRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.LinkedList

@Service
class CurrencyConverterService(
    private val exchangeRateRepository: ExchangeRateRepository
) {

    fun convert(amount: BigDecimal, from: Currency, to: Currency): ConversionResult {
        if (from == to) {
            return ConversionResult(
                targetAmount = amount.setScale(to.decimalPlaces, RoundingMode.HALF_UP),
                from = from,
                to = to,
                steps = emptyList(),
                updatedAt = Instant.now(),
                isVolatile = false
            )
        }

        val allRates = exchangeRateRepository.findAllLatest()
        val path = findShortestPath(from, to, allRates)
            ?: throw ConversionException("No conversion path found from $from to $to")

        var currentAmount = amount
        val steps = mutableListOf<ConversionStep>()
        var minUpdatedAt = Instant.MAX
        var anyVolatile = false

        for (step in path) {
            currentAmount = currentAmount.multiply(step.rate)
            steps.add(step)
            if (step.sourceExchangeRate.updatedAt.isBefore(minUpdatedAt)) {
                minUpdatedAt = step.sourceExchangeRate.updatedAt
            }
            if (step.sourceExchangeRate.isVolatile) {
                anyVolatile = true
            }
        }

        return ConversionResult(
            targetAmount = currentAmount.setScale(to.decimalPlaces, RoundingMode.HALF_UP),
            from = from,
            to = to,
            steps = steps,
            updatedAt = if (minUpdatedAt == Instant.MAX) Instant.now() else minUpdatedAt,
            isVolatile = anyVolatile
        )
    }

    private fun findShortestPath(from: Currency, to: Currency, allRates: List<ExchangeRate>): List<ConversionStep>? {
        val adjacency = mutableMapOf<Currency, MutableList<ConversionStep>>()
        for (rate in allRates) {
            // Forward
            adjacency.getOrPut(rate.baseCurrency) { mutableListOf() }.add(
                ConversionStep(rate.baseCurrency, rate.quoteCurrency, rate.rate, rate)
            )
            // Backward (inverse)
            if (rate.rate > BigDecimal.ZERO) {
                val inverseRate = BigDecimal.ONE.divide(rate.rate, 18, RoundingMode.HALF_UP)
                adjacency.getOrPut(rate.quoteCurrency) { mutableListOf() }.add(
                    ConversionStep(rate.quoteCurrency, rate.baseCurrency, inverseRate, rate)
                )
            }
        }

        val queue: LinkedList<Pair<Currency, List<ConversionStep>>> = LinkedList()
        queue.add(from to emptyList())
        val visited = mutableSetOf(from)

        while (queue.isNotEmpty()) {
            val (currentCurrency, currentPath) = queue.poll()

            if (currentCurrency == to) {
                return currentPath
            }

            adjacency[currentCurrency]?.forEach { step ->
                if (step.quoteCurrency !in visited) {
                    visited.add(step.quoteCurrency)
                    queue.add(step.quoteCurrency to (currentPath + step))
                }
            }
        }

        return null
    }
}
