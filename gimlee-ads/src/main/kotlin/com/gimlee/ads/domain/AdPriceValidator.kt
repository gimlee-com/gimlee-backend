package com.gimlee.ads.domain

import com.gimlee.ads.config.AdPriceProperties
import com.gimlee.ads.domain.AdService.AdOperationException
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.payments.domain.service.CurrencyConverterService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class AdPriceValidator(
    private val properties: AdPriceProperties,
    private val currencyConverterService: CurrencyConverterService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun validatePrice(newPrice: CurrencyAmount, oldPrice: CurrencyAmount?) {
        // 1. Check if new price exceeds global limit (converted to limit currency)
        val limitAmount = properties.amount
        val limitCurrency = properties.currency

        try {
            val convertedNewPrice = currencyConverterService.convert(
                newPrice.amount,
                newPrice.currency,
                limitCurrency
            )

            if (convertedNewPrice.targetAmount > limitAmount) {
                // Limit exceeded. Check for grandfathering.
                if (oldPrice != null) {
                    // Check if old price also exceeded limit?
                    // The requirement says: "allow editing an ad which already exceeds the limit as long as the seller doesn't increase the price"
                    // This implies we compare new price with old price in the original currency.
                    
                    if (newPrice.currency != oldPrice.currency) {
                         // If currency changed, we must enforce the limit strictly because we can't easily say if price "increased" without conversion,
                         // and if we convert, we are back to the limit check.
                         // Or we could convert old price to new currency and compare?
                         // Let's assume strict limit enforcement on currency change to avoid complexity/loopholes.
                         log.warn("Price limit exceeded and currency changed. Rejecting. New: {}, Limit: {} {}", convertedNewPrice.targetAmount, limitAmount, limitCurrency)
                         throw AdOperationException(AdOutcome.PRICE_EXCEEDS_LIMIT)
                    }

                    if (newPrice.amount > oldPrice.amount) {
                        log.warn("Price limit exceeded and price increased. Rejecting. New: {}, Old: {}, Limit: {} {}", newPrice.amount, oldPrice.amount, limitAmount, limitCurrency)
                        throw AdOperationException(AdOutcome.PRICE_EXCEEDS_LIMIT)
                    }
                    // Allowed (grandfathered)
                } else {
                    // New ad or setting price for the first time -> strict limit
                    log.warn("Price limit exceeded for new price. Rejecting. New: {}, Limit: {} {}", convertedNewPrice.targetAmount, limitAmount, limitCurrency)
                    throw AdOperationException(AdOutcome.PRICE_EXCEEDS_LIMIT)
                }
            }
        } catch (e: Exception) {
            if (e is AdOperationException) throw e
            log.error("Failed to convert currency for price validation", e)
            // If conversion fails, should we block? Ideally yes, for safety.
            // But maybe we should allow if same currency?
            // If currencies are same, we can check directly.
            if (newPrice.currency == limitCurrency) {
                 if (newPrice.amount > limitAmount) {
                     if (oldPrice != null && oldPrice.currency == newPrice.currency && newPrice.amount <= oldPrice.amount) {
                         return // Grandfathered
                     }
                     throw AdOperationException(AdOutcome.PRICE_EXCEEDS_LIMIT)
                 }
            } else {
                // If conversion fails and currencies differ, we can't validate. 
                // Fail safe: reject or log error?
                // Given it's a limit, maybe let it pass with error log? Or block?
                // Blocking is safer for "limit".
                throw AdOperationException(AdOutcome.UPDATE_FAILED) // Generic failure
            }
        }
    }
}
