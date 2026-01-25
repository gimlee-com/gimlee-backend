package com.gimlee.payments.rest

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ConversionResult
import com.gimlee.payments.domain.service.CurrencyConverterService
import com.gimlee.payments.rest.dto.CurrencyDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/payments/currency")
@Tag(name = "Currency", description = "Currency conversion and exchange rates")
class CurrencyController(
    private val currencyConverterService: CurrencyConverterService,
    private val messageSource: MessageSource
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

    @GetMapping("/list")
    @Operation(
        summary = "List all supported currencies",
        description = "Returns a list of all supported fiat and crypto currencies with their localized names and metadata."
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of currencies retrieved successfully",
        content = [Content(array = ArraySchema(schema = Schema(implementation = CurrencyDto::class)))]
    )
    fun listCurrencies(): List<CurrencyDto> {
        val locale = LocaleContextHolder.getLocale()
        return Currency.entries.map { currency ->
            val localizedName = messageSource.getMessage(currency.messageKey, null, currency.name, locale)
                ?: currency.name
            CurrencyDto.fromDomain(currency, localizedName)
        }
    }
}
