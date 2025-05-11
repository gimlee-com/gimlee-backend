package com.gimlee.location.cities

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import com.gimlee.location.cities.data.City
import com.gimlee.location.cities.domain.CitySuggestion
import com.gimlee.location.cities.search.CitySearch

@RestController
class CitiesController(
    private val citySearch: CitySearch
) {
    @GetMapping(path = ["/cities/suggestions"])
    fun getCitySuggestions(
        @RequestParam(name = "p") phrase: String
    ): List<CitySuggestion> {
        return citySearch.getSuggestions(phrase)
    }

    @GetMapping(path = ["/cities/{id}"])
    fun getCityById(
        @PathVariable(name = "id", required = true) id: String
    ): City {
        return citySearch.getCityById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
}
