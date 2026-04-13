package com.gimlee.notifications.domain

/**
 * Provides the list of user IDs watching a given ad. Implemented by the application
 * layer to decouple gimlee-notifications from gimlee-ads.
 */
interface AdWatcherProvider {
    fun getWatcherUserIds(adId: String): List<String>
}
