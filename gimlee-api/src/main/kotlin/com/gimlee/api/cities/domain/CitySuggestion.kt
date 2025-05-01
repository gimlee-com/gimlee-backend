package com.gimlee.api.cities.domain

import com.gimlee.api.cities.data.City

data class CitySuggestion(
    val city: City,
    val score: Float
)