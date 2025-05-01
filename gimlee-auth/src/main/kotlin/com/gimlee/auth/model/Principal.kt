package com.gimlee.auth.model

class Principal(
    val userId: String,
    val username: String,
    val roles: List<Role>
)