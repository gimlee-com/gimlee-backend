package com.gimlee.ads.web.dto.response

import com.gimlee.location.cities.persistence.model.CityDocument

data class CityDetailsDto(
    val id: String,
    val name: String,
    val adminDivision: String?,
    val region: String?,
    val district: String?,
    val countryCode: String
) {
    companion object {
        fun fromCityDocument(doc: CityDocument): CityDetailsDto {
            return CityDetailsDto(
                id = doc.id,
                name = doc.nm,
                adminDivision = doc.adm1,
                region = doc.adm1Nm,
                district = doc.adm2Nm,
                countryCode = doc.cc
            )
        }
    }
}