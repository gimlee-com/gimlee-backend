package com.gimlee.common.web.dto

import com.gimlee.common.domain.model.StatusCode

private val statusResponses = StatusCode.entries
    .associateBy({ it }, { StatusResponseDto(it.isSuccess, it.code) })

data class StatusResponseDto(
    val success: Boolean = false,
    val code: Int
) {
    companion object {
        fun fromStatusCode(statusCode: StatusCode) = statusResponses[statusCode] ?: error("Unknown status code")
    }
}