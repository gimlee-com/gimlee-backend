package com.gimlee.mediastore.exception

class MediaUploadException : RuntimeException {

    constructor(s: String) : super(s)

    constructor(s: String, throwable: Throwable) : super(s, throwable)
}
