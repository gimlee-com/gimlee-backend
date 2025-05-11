package com.gimlee.ads.web.dto.response

import com.gimlee.location.cities.data.City
import com.gimlee.location.cities.data.Country

data class CityDetailsDto(
    val id: String,
    val name: String,
    val district: String?,
    val adm1: String, // Administrative division (e.g., province, voivodeship)
    val adm2: String, // Secondary administrative division (e.g., county, powiat)
    val country: Country
) {
    companion object {
        fun fromCity(city: City): CityDetailsDto {
            return CityDetailsDto(
                id = city.id,
                name = city.name,
                district = city.district,
                adm1 = city.adm1,
                adm2 = city.adm2,
                country = city.country
            )
        }
    }
}