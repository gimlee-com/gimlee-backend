package com.gimlee.common.persistence.mongo

object MongoExceptionUtils {
    const val DUPLICATE_KEY_ERROR_CODE = "E11000"

    fun isDuplicateKeyException(e: Exception): Boolean {
        return e.message?.contains(DUPLICATE_KEY_ERROR_CODE) == true || e.message?.contains("duplicate key") == true
    }
}
