package com.gimlee.common.web.dto

import com.gimlee.common.domain.model.Outcome

data class StatusResponseDto(
    val success: Boolean,
    val status: String,
    val message: String? = null,
    val data: Any? = null
) {
    companion object {
        fun fromOutcome(outcome: Outcome, message: String? = null, data: Any? = null): StatusResponseDto {
            return StatusResponseDto(
                success = outcome.httpCode in 200..299,
                status = outcome.code,
                message = message,
                data = data
            )
        }
    }
}