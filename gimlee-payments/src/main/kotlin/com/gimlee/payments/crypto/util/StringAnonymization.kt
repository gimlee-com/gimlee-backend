package com.gimlee.payments.crypto.util

/**
 * Anonymizes a string for logging, adjusting based on length.
 * Returns "ab...yz" (first/last 2) for lengths 5-16,
 * "abc...xyz" (first/last 3) for lengths > 16.
 * Handles null input ("<null>") and short strings ("<hidden>", length <= 4).
 *
 * @param address The string to anonymize. Can be null.
 * @return The anonymized string.
 */

fun anonymize(address: String?): String {
    if (address == null) return "<null>"
    return if (address.length <= 4) {
        "<hidden>"
    } else if (address.length <= 16) {
        "${address.substring(0, 2)}...${address.substring(address.length - 2)}"
    } else {
        "${address.substring(0, 3)}...${address.substring(address.length - 3)}"
    }
}
