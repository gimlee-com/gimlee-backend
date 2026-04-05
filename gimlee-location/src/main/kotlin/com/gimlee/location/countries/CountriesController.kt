package com.gimlee.location.countries

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@Tag(name = "Countries", description = "Endpoints for retrieving country information")
@RestController
@RequestMapping("/location/countries")
class CountriesController(private val countryService: CountryService) {

    @Operation(
        summary = "List All Countries",
        description = "Returns a list of all countries with ISO 3166-1 alpha-2 codes and localized display names. " +
                "Country names are translated based on the Accept-Language header. " +
                "Results are sorted alphabetically by the localized country name."
    )
    @Parameter(
        name = "Accept-Language",
        description = "Locale for country name translation (e.g., en-US, pl-PL). Defaults to English.",
        example = "pl-PL",
        `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of countries with localized names",
        content = [Content(array = ArraySchema(schema = Schema(implementation = CountryDto::class)))]
    )
    @GetMapping("/")
    fun listCountries(): ResponseEntity<List<CountryDto>> {
        val locale = LocaleContextHolder.getLocale()
        val countries = countryService.getCountries(locale)

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
            .body(countries)
    }
}
