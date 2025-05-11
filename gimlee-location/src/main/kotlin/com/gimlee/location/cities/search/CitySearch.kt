package com.gimlee.location.cities.search

import com.gimlee.location.cities.data.City
import com.gimlee.location.cities.domain.CitySuggestion

interface CitySearch {
    fun getSuggestions(phrase: String): List<CitySuggestion>
    fun getCityById(id: String): City?
}
