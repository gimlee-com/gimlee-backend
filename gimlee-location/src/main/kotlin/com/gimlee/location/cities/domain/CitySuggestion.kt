package com.gimlee.location.cities.domain

import com.gimlee.location.cities.data.City

data class CitySuggestion(
    val city: City,
    val score: Float
)