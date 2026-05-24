package com.gimlee.auth.domain.auth

import com.gimlee.auth.domain.AuthOutcome

sealed class RefreshResult {
    data class Success(
        val userId: String,
        val newPlaintextToken: String
    ) : RefreshResult()

    data class Failure(
        val outcome: AuthOutcome
    ) : RefreshResult()
}
