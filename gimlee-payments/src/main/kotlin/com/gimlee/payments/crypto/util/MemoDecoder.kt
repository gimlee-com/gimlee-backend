package com.gimlee.payments.crypto.util

import java.nio.charset.StandardCharsets

object MemoDecoder {

    /**
     * Decodes a hex-encoded, null-padded memo string into a readable UTF-8 string.
     *
     * @param hexMemo The hex string from the RPC response (e.g., "7465737400...").
     * @return The decoded string (e.g., "test").
     */
    fun decodeHexMemo(hexMemo: String?): String? {
        if (hexMemo.isNullOrBlank()) {
            return null
        }

        if (hexMemo.length % 2 != 0) {
            error("Malformed memo: $hexMemo")
        }

        return try {
            val bytes = hexMemo.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

            // Find the index of the first null byte (padding starts here)
            val firstNullIndex = bytes.indexOf(0.toByte())

            // Determine the length of the actual content
            // If no null byte is found, use the entire array length.
            val contentLength = if (firstNullIndex == -1) bytes.size else firstNullIndex

            // If the content length is 0 (e.g., memo was just "0000..."), return empty string.
            if (contentLength <= 0) {
                ""
            } else {
                // Convert the relevant part of the byte array to a String using UTF-8
                String(bytes, 0, contentLength, StandardCharsets.UTF_8)
            }
        } catch (e: NumberFormatException) {
            // Handle cases where the string contains non-hex characters
            error("Error decoding hex memo: Invalid hex character found. Memo: $hexMemo")
        } catch (e: Exception) {
            error("Error decoding hex memo: ${e.message}. Memo: $hexMemo")
        }
    }
}