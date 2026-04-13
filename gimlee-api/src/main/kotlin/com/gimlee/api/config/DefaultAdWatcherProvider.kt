package com.gimlee.api.config

import com.gimlee.ads.domain.WatchlistService
import com.gimlee.notifications.domain.AdWatcherProvider
import org.springframework.stereotype.Component

@Component
class DefaultAdWatcherProvider(
    private val watchlistService: WatchlistService
) : AdWatcherProvider {

    override fun getWatcherUserIds(adId: String): List<String> {
        return watchlistService.getWatcherUserIds(adId)
    }
}
