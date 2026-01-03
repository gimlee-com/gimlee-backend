package com.gimlee.auth.exception

class AuthorizationException(message: String, val resource: String? = null) : RuntimeException(message)
