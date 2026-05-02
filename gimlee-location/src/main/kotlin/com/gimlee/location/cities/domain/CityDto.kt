package com.gimlee.location.cities.domain

import com.gimlee.location.cities.persistence.model.CityDocument
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Detailed city information from GeoNames.")
data class CityDto(
    @Schema(description = "GeoNames city ID", example = "756135")
    val id: String,

    @Schema(description = "City name (may be localized based on Accept-Language header)", example = "Warsaw")
    val name: String,

    @Schema(description = "ISO 3166-1 alpha-2 country code", example = "PL")
    val countryCode: String,

    @Schema(description = "Admin1 division code (state/province)", example = "78")
    val adminDivision: String?,

    @Schema(description = "Admin1 region name (state/province/voivodeship), localized when available", example = "Masovian Voivodeship")
    val region: String?,

    @Schema(description = "Admin2 district name (county/district), localized when available", example = "Warsaw")
    val district: String?,

    @Schema(description = "City population", example = "1790658")
    val population: Long,

    @Schema(description = "Latitude", example = "52.2298")
    val latitude: Double,

    @Schema(description = "Longitude", example = "21.0118")
    val longitude: Double,

    @Schema(description = "Timezone", example = "Europe/Warsaw")
    val timezone: String?
) {
    companion object {
        fun fromDocument(
            doc: CityDocument,
            localizedName: String? = null,
            localizedRegion: String? = null,
            localizedDistrict: String? = null
        ): CityDto {
            return CityDto(
                id = doc.id,
                name = localizedName ?: doc.nm,
                countryCode = doc.cc,
                adminDivision = doc.adm1,
                region = localizedRegion ?: doc.adm1Nm,
                district = localizedDistrict ?: doc.adm2Nm,
                population = doc.pop,
                latitude = doc.lat,
                longitude = doc.lon,
                timezone = doc.tz
            )
        }
    }
}
