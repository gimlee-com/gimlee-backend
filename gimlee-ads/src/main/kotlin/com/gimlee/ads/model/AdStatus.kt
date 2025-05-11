package com.gimlee.ads.model

/**
 * Represents the lifecycle status of an ad.
 */
enum class AdStatus {
    /** Ad is visible and active in the marketplace. */
    ACTIVE,

    /** Ad is created but not yet visible, pending completion or activation. */
    INACTIVE,

    /** Ad has been removed by the user or system. */
    DELETED
}