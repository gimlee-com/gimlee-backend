package com.gimlee.ads.web.dto.request

data class LocationDto(
    val cityId: String,
    val point: DoubleArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationDto

        if (cityId != other.cityId) return false
        if (point != null) {
            if (other.point == null) return false
            if (!point.contentEquals(other.point)) return false
        } else if (other.point != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cityId.hashCode()
        result = 31 * result + (point?.contentHashCode() ?: 0)
        return result
    }
}