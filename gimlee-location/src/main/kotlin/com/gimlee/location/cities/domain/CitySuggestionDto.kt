package com.gimlee.location.cities.domain

import com.gimlee.location.cities.geonames.index.CitySearchResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "City suggestion returned from search. Name is localized when a matching language is available.")
data class CitySuggestionDto(
    @Schema(description = "GeoNames city ID", example = "756135")
    val id: String,

    @Schema(description = "City name, localized to the requested language when available", example = "Warsaw")
    val name: String,

    @Schema(description = "ISO 3166-1 alpha-2 country code", example = "PL")
    val countryCode: String,

    @Schema(description = "Admin1 region name (state/province/voivodeship), localized when available", example = "Masovian Voivodeship")
    val region: String?,

    @Schema(description = "Admin2 district name (county/district), localized when available", example = "Warsaw")
    val district: String?,

    @Schema(description = "City population", example = "1790658")
    val population: Long,

    @Schema(description = "Latitude", example = "52.2298")
    val latitude: Double,

    @Schema(description = "Longitude", example = "21.0118")
    val longitude: Double
) {
    companion object {
        fun fromSearchResult(
            result: CitySearchResult,
            localizedRegion: String? = null,
            localizedDistrict: String? = null
        ): CitySuggestionDto {
            return CitySuggestionDto(
                id = result.geonameId,
                name = result.displayName,
                countryCode = result.countryCode,
                region = localizedRegion ?: result.admin1Name,
                district = localizedDistrict ?: result.admin2Name,
                population = result.population,
                latitude = result.latitude,
                longitude = result.longitude
            )
        }
    }
}
