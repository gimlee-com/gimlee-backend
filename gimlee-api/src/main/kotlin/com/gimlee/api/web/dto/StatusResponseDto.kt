package com.gimlee.api.web.dto

import com.gimlee.api.domain.StatusCode

private val statusResponses = StatusCode.values()
    .associateBy({ it }, { StatusResponseDto(it.isSuccess, it.code) })

data class StatusResponseDto(
    val success: Boolean = false,
    val code: Int
) {
    companion object {
        fun fromStatusCode(statusCode: StatusCode) = statusResponses[statusCode] ?: error("Unknown status code")
    }
}