package com.gimlee.ads.domain

import com.gimlee.ads.persistence.WatchlistRepository
import com.gimlee.events.AdAddedToWatchlistEvent
import com.gimlee.events.AdRemovedFromWatchlistEvent
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class WatchlistService(
    private val watchlistRepository: WatchlistRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun addToWatchlist(userId: String, adId: String) {
        val userObjectId = ObjectId(userId)
        val adObjectId = ObjectId(adId)
        
        if (watchlistRepository.add(userObjectId, adObjectId)) {
            eventPublisher.publishEvent(AdAddedToWatchlistEvent(userId, adId))
        }
    }

    fun removeFromWatchlist(userId: String, adId: String) {
        val userObjectId = ObjectId(userId)
        val adObjectId = ObjectId(adId)
        
        if (watchlistRepository.remove(userObjectId, adObjectId)) {
            eventPublisher.publishEvent(AdRemovedFromWatchlistEvent(userId, adId))
        }
    }

    fun getWatchlist(userId: String): List<String> {
        return watchlistRepository.findAllByUserId(ObjectId(userId))
            .map { it.adId.toHexString() }
    }

    fun isInWatchlist(userId: String, adId: String): Boolean {
        return watchlistRepository.exists(ObjectId(userId), ObjectId(adId))
    }
}
