package com.gimlee.api.playground.media.data

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import com.gimlee.mediastore.persistence.MediaItemRepository
import com.gimlee.mediastore.domain.MediaItem
import java.time.Duration
import kotlin.random.Random

private val mediaItemsCache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(1))
    .build<String, List<MediaItem>>()

@Lazy(true)
@Component
class PlaygroundMediaRepository(
    private val mediaItemRepository: MediaItemRepository
) {
    companion object {
        private const val MEDIA_ITEM_PLAYGROUND_CACHE_KEY = "mediaItems"
    }

    fun getRandomMediaItem(): MediaItem {
        val allMediaItems: List<MediaItem> = mediaItemsCache.get(MEDIA_ITEM_PLAYGROUND_CACHE_KEY) {
            mediaItemRepository.findAll()
        }!!
        val randomIndex = Random.nextInt(0, allMediaItems.size)
        return allMediaItems[randomIndex]
    }
}