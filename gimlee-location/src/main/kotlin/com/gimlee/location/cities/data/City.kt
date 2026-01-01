package com.gimlee.location.cities.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonIgnoreProperties(ignoreUnknown = true)
data class City(
    var id: String = "",
    var country: Country = Country.PL,
    var adm1: String = "",
    var adm2: String = "",
    var name: String = "",
    var district: String? = null,
    @set:JsonDeserialize(using = DMSToDecimalDeserializer::class)
    var lat: Double = 0.0,
    @set:JsonDeserialize(using = DMSToDecimalDeserializer::class)
    var lon: Double = 0.0
) {
    companion object {
        fun empty() = City(
            id = "",
            country = Country.PL,
            adm1 = "",
            adm2 = "",
            name = "",
            lat = 0.0,
            lon = 0.0
        )
    }
}