package com.gimlee.common

import java.time.Instant

/**
 * Converts this Instant to the number of microseconds since the epoch of 1970-01-01T00:00:00Z.
 *
 * @return The number of microseconds since the epoch.
 */
fun Instant.toMicros(): Long =
    Math.addExact(Math.multiplyExact(this.epochSecond, 1_000_000L), (this.nano / 1_000).toLong())

/**
 * Contains static utility functions for Instant, like conversions from microseconds.
 */
object InstantUtils {
    private const val MICROS_PER_SECOND = 1_000_000L
    private const val NANOS_PER_MICRO = 1_000L

    /**
     * Obtains an instance of Instant using microseconds from the epoch of 1970-01-01T00:00:00Z.
     *
     * @param micros The number of microseconds since the epoch.
     * @return An Instant representing the given number of microseconds since the epoch.
     */
    fun fromMicros(micros: Long): Instant {
        val seconds = Math.floorDiv(micros, MICROS_PER_SECOND)
        val microAdjustment = Math.floorMod(micros, MICROS_PER_SECOND)
        val nanoAdjustment = microAdjustment * NANOS_PER_MICRO
        return Instant.ofEpochSecond(seconds, nanoAdjustment)
    }
}
