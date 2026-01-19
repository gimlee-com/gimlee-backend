package com.gimlee.payments.rest

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ConversionResult
import com.gimlee.payments.domain.service.CurrencyConverterService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/payments/currency")
@Tag(name = "Currency", description = "Currency conversion and exchange rates")
class CurrencyController(
    private val currencyConverterService: CurrencyConverterService
) {

    @GetMapping("/convert")
    @Operation(summary = "Convert currency targetAmount")
    fun convert(
        @RequestParam amount: BigDecimal,
        @RequestParam from: Currency,
        @RequestParam to: Currency
    ): ConversionResult {
        return currencyConverterService.convert(amount, from, to)
    }
}
