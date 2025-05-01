package com.gimlee.auth.exception

class AuthenticationException : RuntimeException {
    constructor(s: String) : super(s)

    constructor(s: String, throwable: Throwable) : super(s, throwable)
}
