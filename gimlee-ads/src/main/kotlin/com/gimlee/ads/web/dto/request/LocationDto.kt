package com.gimlee.ads.web.dto.request

data class LocationDto(
    val cityId: String,
    val point: DoubleArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationDto

        if (cityId != other.cityId) return false
        if (!point.contentEquals(other.point)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cityId.hashCode()
        result = 31 * result + point.contentHashCode()
        return result
    }
}