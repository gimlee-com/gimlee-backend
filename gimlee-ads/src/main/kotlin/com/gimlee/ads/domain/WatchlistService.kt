package com.gimlee.ads.domain

import com.gimlee.ads.persistence.WatchlistRepository
import com.gimlee.events.AdWatchlistEvent
import com.gimlee.events.WatchlistEventType
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
            eventPublisher.publishEvent(AdWatchlistEvent(userId, adId, WatchlistEventType.ADDED))
        }
    }

    fun removeFromWatchlist(userId: String, adId: String) {
        val userObjectId = ObjectId(userId)
        val adObjectId = ObjectId(adId)
        
        if (watchlistRepository.remove(userObjectId, adObjectId)) {
            eventPublisher.publishEvent(AdWatchlistEvent(userId, adId, WatchlistEventType.REMOVED))
        }
    }

    fun getWatchlist(userId: String): List<String> {
        return watchlistRepository.findAllByUserId(ObjectId(userId))
            .map { it.adId.toHexString() }
    }

    fun isInWatchlist(userId: String, adId: String): Boolean {
        return watchlistRepository.exists(ObjectId(userId), ObjectId(adId))
    }

    fun getWatchedAdIds(userId: String, adIds: List<String>): Set<String> {
        if (adIds.isEmpty()) return emptySet()
        val objectIds = adIds.map { ObjectId(it) }
        return watchlistRepository.findWatchedAdIds(ObjectId(userId), objectIds)
            .map { it.toHexString() }
            .toSet()
    }

    fun getWatchersCount(adId: String): Long {
        return watchlistRepository.countByAdId(ObjectId(adId))
    }
}
