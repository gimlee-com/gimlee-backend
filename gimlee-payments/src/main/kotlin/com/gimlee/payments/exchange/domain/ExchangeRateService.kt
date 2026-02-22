package com.gimlee.payments.exchange.domain

import com.gimlee.payments.domain.service.VolatilityStateService
import com.gimlee.payments.persistence.ExchangeRateRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ExchangeRateService(
    private val exchangeRateFetcher: ExchangeRateFetcher,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val volatilityStateService: VolatilityStateService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${gimlee.payments.exchange.update-interval-ms:60000}")
    fun updateRates() {
        log.info("Starting exchange rates update...")
        val rates = exchangeRateFetcher.fetchAllLatestRates()
        rates.forEach {
            exchangeRateRepository.save(it)
        }
        log.info("Exchange rates update completed. Total rates updated: ${rates.size}")
        
        // Update global volatility state based on new rates
        volatilityStateService.updateVolatilityStates()
    }
}
