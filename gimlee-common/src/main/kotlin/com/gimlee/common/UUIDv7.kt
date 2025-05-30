package com.gimlee.common

import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object UUIDv7 {
    private val random = SecureRandom()

    @OptIn(ExperimentalUuidApi::class)
    fun generate(): UUID {
        val value = ByteArray(16)
        random.nextBytes(value)
        val timestamp = Instant.now().toEpochMilli()

        // timestamp
        value[0] = ((timestamp shr 40) and 0xFF).toByte()
        value[1] = ((timestamp shr 32) and 0xFF).toByte()
        value[2] = ((timestamp shr 24) and 0xFF).toByte()
        value[3] = ((timestamp shr 16) and 0xFF).toByte()
        value[4] = ((timestamp shr 8) and 0xFF).toByte()
        value[5] = (timestamp and 0xFF).toByte()

        // version and variant
        value[6] = (value[6].toInt() and 0x0F or 0x70).toByte()
        value[8] = (value[8].toInt() and 0x3F or 0x80).toByte()

        return Uuid.Companion.fromByteArray(value).toJavaUuid()
    }
}