package com.gimlee.location.cities

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import com.gimlee.location.cities.data.City
import com.gimlee.location.cities.domain.CitySuggestion
import com.gimlee.location.cities.search.CitySearch

@Tag(name = "Cities", description = "Endpoints for searching and retrieving city information")
@RestController
class CitiesController(
    private val citySearch: CitySearch
) {
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
    @ApiResponse(responseCode = "200", description = "Detailed city information")
    @ApiResponse(responseCode = "404", description = "City not found")
    @GetMapping(path = ["/cities/{id}"])
    fun getCityById(
        @Parameter(description = "Unique ID of the city")
        @PathVariable(name = "id", required = true) id: String
    ): City {
        return citySearch.getCityById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
}
