package com.gimlee.common.domain.model

enum class StatusCode private constructor(val code: Int, val isSuccess: Boolean) {
    SUCCESS(0, true),
    UNAUTHORIZED(1, false),

    INCORRECT_CREDENTIALS(2, false),
    MISSING_CREDENTIALS(3, false);


    companion object {
        fun parse(statusCode: Int): StatusCode {
            for (value in entries) {
                if (value.code == statusCode) {
                    return value
                }
            }
            throw IllegalArgumentException("Unknown status code: $statusCode")
        }
    }
}