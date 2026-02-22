package com.gimlee.payments.rest

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.domain.service.VolatilityStateService
import com.gimlee.payments.rest.dto.VolatilityStatusDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments/volatility")
@Tag(name = "Volatility", description = "Currency volatility status and monitoring")
class VolatilityController(
    private val volatilityStateService: VolatilityStateService,
    private val paymentProperties: PaymentProperties
) {

    @GetMapping("/status")
    @Operation(summary = "Get current volatility status for monitored currencies")
    fun getStatus(): List<VolatilityStatusDto> {
        val cooldownSeconds = paymentProperties.volatility.cooldownSeconds
        
        // We only monitor ARRR and YEC currently
        val monitoredCurrencies = listOf(Currency.ARRR, Currency.YEC)
        
        return monitoredCurrencies.map { currency ->
            val state = volatilityStateService.getVolatilityState(currency)
            VolatilityStatusDto(
                currency = currency.name,
                isVolatile = state.isVolatile,
                startTime = state.startTime,
                cooldownEndsAt = state.cooldownEndsAt(cooldownSeconds),
                currentDropPct = state.currentDropPct,
                maxPriceInWindow = state.maxPriceInWindow,
                isStale = state.isStale
            )
        }
    }
}
