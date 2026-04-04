package com.gimlee.location.cities.domain

import com.gimlee.common.domain.model.Outcome

enum class LocationOutcome(override val httpCode: Int) : Outcome {
    CITY_NOT_FOUND(404),
    GEOIP_COUNTRY_DETECTED(200),
    GEOIP_COUNTRY_UNKNOWN(404),
    GEOIP_UNAVAILABLE(503);

    override val code: String get() = "LOCATION_$name"
    override val messageKey: String get() = "status.location.${name.replace("_", "-").lowercase()}"
}
