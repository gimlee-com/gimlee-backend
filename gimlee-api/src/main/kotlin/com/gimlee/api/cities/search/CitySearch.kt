package com.gimlee.api.cities.search

import com.gimlee.api.cities.data.City
import com.gimlee.api.cities.domain.CitySuggestion

interface CitySearch {
    fun getSuggestions(phrase: String): List<CitySuggestion>
    fun getCityById(id: String): City?
}
