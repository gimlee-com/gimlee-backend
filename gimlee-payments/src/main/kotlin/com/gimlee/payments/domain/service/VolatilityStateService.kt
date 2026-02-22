package com.gimlee.payments.domain.service

import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.persistence.ExchangeRateRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class VolatilityStateService(
    private val paymentProperties: PaymentProperties,
    private val exchangeRateRepository: ExchangeRateRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class VolatilityState(
        val isVolatile: Boolean = false,
        val startTime: Instant? = null,
        val maxPriceInWindow: BigDecimal? = null,
        val currentDropPct: BigDecimal? = null,
        val lastUpdated: Instant = Instant.now(),
        val isStale: Boolean = false
    ) {
        fun cooldownEndsAt(cooldownSeconds: Long): Instant? {
            return startTime?.plusSeconds(cooldownSeconds)
        }
    }

    private val volatilityStates = ConcurrentHashMap<Currency, VolatilityState>()

    // Monitored currencies against USDT (stable reference)
    private val monitoredPairs = listOf(
        Currency.ARRR to Currency.USDT,
        Currency.YEC to Currency.USDT
    )

    fun isVolatile(currency: Currency): Boolean {
        return volatilityStates[currency]?.isVolatile == true
    }

    fun isFrozen(currency: Currency): Boolean {
        val state = volatilityStates[currency]
        return state?.isVolatile == true || state?.isStale == true
    }

    fun getVolatilityState(currency: Currency): VolatilityState {
        return volatilityStates[currency] ?: VolatilityState()
    }

    @PostConstruct
    fun init() {
        // Initial run to populate cache
        updateVolatilityStates()
    }

    fun updateVolatilityStates() {
        val now = Instant.now()
        val windowSeconds = paymentProperties.volatility.windowSeconds
        val threshold = BigDecimal.valueOf(paymentProperties.volatility.downsideThreshold)
        val cooldownSeconds = paymentProperties.volatility.cooldownSeconds
        val staleThresholdSeconds = paymentProperties.volatility.staleThresholdSeconds

        monitoredPairs.forEach { (base, quote) ->
            try {
                // Check for staleness first
                val latestRate = exchangeRateRepository.findLatest(base, quote)
                
                if (latestRate == null || 
                    latestRate.updatedAt.isBefore(now.minusSeconds(staleThresholdSeconds))) {
                    
                    val currentState = volatilityStates[base] ?: VolatilityState()
                    if (!currentState.isStale) {
                         log.warn("Volatility Status: {} is STALE. Last update: {}", base, latestRate?.updatedAt)
                    }
                    
                    volatilityStates[base] = currentState.copy(
                        isStale = true,
                        // Maintain previous volatility state? Or clear it?
                        // If it's stale, we don't know the price, so volatility calculation is impossible.
                        // But we should probably not clear the 'isVolatile' flag if it was already set, 
                        // just to be safe? Or maybe 'isFrozen' handles it.
                        // Let's keep other fields as is, but mark as stale.
                        lastUpdated = now
                    )
                    return@forEach
                }

                // Not stale, proceed to volatility check
                // Reset stale flag if it was stale
                // Fetch rates in window
                val windowStart = now.minusSeconds(windowSeconds)
                val rates = exchangeRateRepository.findRatesInWindow(
                    base, quote, 
                    windowStart.toMicros(), 
                    now.toMicros()
                )

                if (rates.isEmpty()) {
                    // This shouldn't happen if findLatest returned something recent, 
                    // unless windowSeconds is very small < staleThreshold (which it is).
                    // If rates in window are empty, but findLatest is recent (e.g. 50 mins ago),
                    // then it is not stale (threshold 1h), but we have no data in 10m window.
                    // This means no trading activity.
                    // Should we trigger volatility? No.
                    // Should we clear volatility? 
                    // If we had volatility before, and now no trades -> we don't know if it recovered.
                    // But if no trades, price didn't drop further.
                    // Let's assume stability if no trades within window but not stale.
                    return@forEach
                }

                // Current rate (latest in window)
                val currentRate = rates.first().rate
                
                // Max rate in window
                val maxRate = rates.maxOf { it.rate }

                if (maxRate.compareTo(BigDecimal.ZERO) == 0) return@forEach

                // Calculate drop: (max - current) / max
                val drop = (maxRate.subtract(currentRate))
                    .divide(maxRate, 4, RoundingMode.HALF_UP)
                
                val currentState = volatilityStates[base] ?: VolatilityState()
                
                // If it was stale, clear stale flag
                val isStale = false

                if (drop >= threshold) {
                    // Trigger volatility
                    // If not volatile before, log it
                    if (!currentState.isVolatile) {
                        log.warn("Volatility TRIGGERED for {}: Drop {}% >= {}% (Max: {}, Current: {})", 
                            base, drop.movePointRight(2), threshold.movePointRight(2), maxRate, currentRate)
                    } else {
                        // Already volatile, we extend cooldown by updating startTime to now
                         log.debug("Volatility CONTINUES for {}: Drop {}% (Max: {}, Current: {}). Cooldown reset.", 
                            base, drop.movePointRight(2), maxRate, currentRate)
                    }
                    
                    volatilityStates[base] = VolatilityState(
                        isVolatile = true,
                        startTime = now, // Reset start time to now
                        maxPriceInWindow = maxRate,
                        currentDropPct = drop,
                        lastUpdated = now,
                        isStale = isStale
                    )
                } else {
                    // Drop is below threshold. Check recovery.
                    if (currentState.isVolatile) {
                        val cooldownEnd = currentState.cooldownEndsAt(cooldownSeconds)
                        
                        if (cooldownEnd != null && now.isAfter(cooldownEnd)) {
                            // Recovered
                            log.info("Volatility RECOVERED for {}: Drop {}% < {}% and cooldown ended at {}", 
                                base, drop.movePointRight(2), threshold.movePointRight(2), cooldownEnd)
                            
                            volatilityStates[base] = VolatilityState(
                                isVolatile = false,
                                startTime = null,
                                maxPriceInWindow = maxRate,
                                currentDropPct = drop,
                                lastUpdated = now,
                                isStale = isStale
                            )
                        } else {
                            // Still in cooldown
                            // Update stats but keep volatile state and original startTime
                            volatilityStates[base] = currentState.copy(
                                maxPriceInWindow = maxRate,
                                currentDropPct = drop,
                                lastUpdated = now,
                                isStale = isStale
                            )
                        }
                    } else {
                        // Normal state remains normal
                         volatilityStates[base] = VolatilityState(
                            isVolatile = false,
                            startTime = null,
                            maxPriceInWindow = maxRate,
                            currentDropPct = drop,
                            lastUpdated = now,
                            isStale = isStale
                        )
                    }
                }

            } catch (e: Exception) {
                log.error("Error updating volatility state for $base", e)
            }
        }
    }
}
