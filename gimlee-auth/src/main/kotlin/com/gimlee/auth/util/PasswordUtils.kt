package com.gimlee.auth.util

import org.apache.commons.codec.binary.Hex
import java.security.MessageDigest
import kotlin.random.Random

private const val SALT_LENGTH_BYTES = 12
private const val DIGEST_ALGORITHM = "SHA-256"

fun generateSalt(): ByteArray = Random.nextBytes(SALT_LENGTH_BYTES)

fun createHexSaltAndPasswordHash(plaintextPassword: String, salt: ByteArray): Pair<String, String> {
    return Pair(
        Hex.encodeHexString(salt),
        createHexPasswordHash(plaintextPassword, salt)
    )
}

fun createHexPasswordHash(plaintextPassword: String, salt: ByteArray): String {
    val saltAndPasswordDigest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        .digest(salt + plaintextPassword.toByteArray(Charsets.UTF_8))

    return Hex.encodeHexString(saltAndPasswordDigest)
}