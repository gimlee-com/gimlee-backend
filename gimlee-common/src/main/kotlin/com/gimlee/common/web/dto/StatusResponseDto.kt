package com.gimlee.common.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.gimlee.common.domain.model.Outcome

data class StatusResponseDto(
    val success: Boolean,
    val status: String,
    val message: String? = null,
    val data: Any? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val fieldErrors: List<FieldErrorDto>? = null
) {
    companion object {
        fun fromOutcome(
            outcome: Outcome,
            message: String? = null,
            data: Any? = null,
            fieldErrors: List<FieldErrorDto>? = null
        ): StatusResponseDto {
            return StatusResponseDto(
                success = outcome.httpCode in 200..299,
                status = outcome.code,
                message = message,
                data = data,
                fieldErrors = fieldErrors
            )
        }
    }
}