package com.gimlee.auth.model

data class Principal(
    val userId: String,
    val username: String,
    val roles: List<Role>
) {
    companion object {
        val EMPTY = Principal("", "", emptyList())
    }
}

fun Principal?.isEmptyOrNull() = this == null || this == Principal.EMPTY