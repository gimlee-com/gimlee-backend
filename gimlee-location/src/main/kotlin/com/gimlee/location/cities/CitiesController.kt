package com.gimlee.location.cities

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.gimlee.location.cities.domain.CityDto
import com.gimlee.location.cities.domain.CitySuggestionDto
import com.gimlee.location.cities.domain.LocationOutcome
import com.gimlee.location.cities.service.CityService
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

@Tag(name = "Cities", description = "Endpoints for searching and retrieving city information. " +
    "Backed by GeoNames dataset with support for all world cities and multilingual names.")
@RestController
class CitiesController(
    private val cityService: CityService,
    private val messageSource: MessageSource
) {
    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Search City Suggestions",
        description = "Searches for cities matching the provided query. Returns a list of city suggestions " +
            "ranked by relevance and population. Supports country filtering and localized city names."
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of city suggestions",
        content = [Content(array = ArraySchema(schema = Schema(implementation = CitySuggestionDto::class)))]
    )
    @ApiResponse(
        responseCode = "503",
        description = "City data is not yet available. Possible status: LOCATION_CITY_SEARCH_UNAVAILABLE",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping(path = ["/cities/suggestions"])
    fun getCitySuggestions(
        @Parameter(description = "Search query (min 2 characters)", required = true, example = "War")
        @RequestParam(name = "q") query: String,

        @Parameter(description = "ISO 3166-1 alpha-2 country code filter", example = "PL")
        @RequestParam(name = "cc", required = false) countryCode: String?,

        @Parameter(description = "IETF language tag for localized names (e.g., en-US, pl-PL)", example = "en-US")
        @RequestParam(name = "lang", required = false) languageTag: String?,

        @Parameter(description = "Max results (1-20, default 10)", example = "10")
        @RequestParam(name = "limit", required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<Any> {
        if (!cityService.isReady()) {
            return handleOutcome(LocationOutcome.CITY_SEARCH_UNAVAILABLE)
        }

        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            return ResponseEntity.ok(emptyList<CitySuggestionDto>())
        }

        val effectiveLimit = limit.coerceIn(1, 20)
        val results = cityService.search(trimmedQuery, countryCode, languageTag, effectiveLimit)

        // Batch resolve localized admin names
        val adminGids = mutableSetOf<String>()
        results.forEach { r ->
            r.admin1GeonameId?.let { adminGids.add(it) }
            r.admin2GeonameId?.let { adminGids.add(it) }
        }
        val localizedAdminNames = cityService.batchResolveAdminNames(adminGids, languageTag)

        val suggestions = results.map { r ->
            CitySuggestionDto.fromSearchResult(
                result = r,
                localizedRegion = r.admin1GeonameId?.let { localizedAdminNames[it] },
                localizedDistrict = r.admin2GeonameId?.let { localizedAdminNames[it] }
            )
        }
        return ResponseEntity.ok(suggestions)
    }

    @Operation(
        summary = "Get City by ID",
        description = "Retrieves detailed information about a specific city by its GeoNames ID. " +
            "City name is localized based on the Accept-Language header or lang parameter."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Detailed city information",
        content = [Content(schema = Schema(implementation = CityDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "City not found. Possible status: LOCATION_CITY_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping(path = ["/cities/{id}"])
    fun getCityById(
        @Parameter(description = "GeoNames city ID", example = "756135")
        @PathVariable(name = "id", required = true) id: String,

        @Parameter(description = "IETF language tag for localized name", example = "en-US")
        @RequestParam(name = "lang", required = false) languageTag: String?
    ): ResponseEntity<Any> {
        val city = cityService.getCityById(id)
            ?: return handleOutcome(LocationOutcome.CITY_NOT_FOUND)

        val localizedName = cityService.getLocalizedCityName(id, languageTag)
        val localizedRegion = cityService.resolveLocalizedAdminName(city.adm1Gid, city.adm1Nm, languageTag)
        val localizedDistrict = cityService.resolveLocalizedAdminName(city.adm2Gid, city.adm2Nm, languageTag)
        return ResponseEntity.ok(CityDto.fromDocument(city, localizedName, localizedRegion, localizedDistrict))
    }
}
