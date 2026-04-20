package com.gimlee.chat.domain.model

/**
 * Abstraction for extracting the authenticated user's identity.
 * Consumers provide their own implementation (e.g., using JWT or session-based auth).
 */
interface ChatPrincipalProvider {
    fun getUserId(): String
    fun getUsername(): String
}
