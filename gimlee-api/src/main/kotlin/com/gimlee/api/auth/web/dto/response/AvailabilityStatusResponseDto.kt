package com.gimlee.api.auth.web.dto.response

data class AvailabilityStatusResponseDto(
    val available: Boolean
) {
    companion object {
        val available = AvailabilityStatusResponseDto(true)
        val notAvailable = AvailabilityStatusResponseDto(false)
    }
}