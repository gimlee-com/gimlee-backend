package com.gimlee.ads.web.dto.response

import com.gimlee.location.cities.persistence.model.CityDocument
import com.gimlee.ads.domain.model.Location as DomainLocation

data class LocationWithCityDetailsDto(
    val city: CityDetailsDto?,
    val point: DoubleArray?,
) {
    companion object {
        fun fromDomain(
            domainLocation: DomainLocation?,
            cityDocument: CityDocument?
        ): LocationWithCityDetailsDto? {
            if (domainLocation == null) return null

            val cityDetails = cityDocument?.let { CityDetailsDto.fromCityDocument(it) }

            return LocationWithCityDetailsDto(
                city = cityDetails,
                point = domainLocation.point
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationWithCityDetailsDto

        if (city != other.city) return false
        if (point != null) {
            if (other.point == null) return false
            if (!point.contentEquals(other.point)) return false
        } else if (other.point != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = city?.hashCode() ?: 0
        result = 31 * result + (point?.contentHashCode() ?: 0)
        return result
    }
}