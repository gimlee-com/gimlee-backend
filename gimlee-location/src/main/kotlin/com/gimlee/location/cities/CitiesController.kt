package com.gimlee.location.cities

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.gimlee.location.cities.data.City
import com.gimlee.location.cities.domain.CitySuggestion
import com.gimlee.location.cities.domain.LocationOutcome
import com.gimlee.location.cities.search.CitySearch
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

@Tag(name = "Cities", description = "Endpoints for searching and retrieving city information")
@RestController
class CitiesController(
    private val citySearch: CitySearch,
    private val messageSource: MessageSource
) {
    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Get City Suggestions",
        description = "Searches for cities matching the provided phrase. Returns a list of city suggestions including their IDs and names."
    )
    @ApiResponse(responseCode = "200", description = "List of city suggestions")
    @GetMapping(path = ["/cities/suggestions/"])
    fun getCitySuggestions(
        @Parameter(description = "Search phrase for city name")
        @RequestParam(name = "p") phrase: String
    ): List<CitySuggestion> {
        return citySearch.getSuggestions(phrase)
    }

    @Operation(
        summary = "Get City by ID",
        description = "Retrieves detailed information about a specific city by its ID."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Detailed city information",
        content = [Content(schema = Schema(implementation = City::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "City not found. Possible status codes: LOCATION_CITY_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping(path = ["/cities/{id}"])
    fun getCityById(
        @Parameter(description = "Unique ID of the city")
        @PathVariable(name = "id", required = true) id: String
    ): ResponseEntity<Any> {
        val city = citySearch.getCityById(id)
        return if (city != null) {
            ResponseEntity.ok(city)
        } else {
            handleOutcome(LocationOutcome.CITY_NOT_FOUND)
        }
    }
}
