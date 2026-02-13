package com.gimlee.common.annotation

/**
 * Annotation to mark controller methods for analytical tracking.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Analytics(
    /**
     * The type of the event (e.g., "AD_VIEW", "SEARCH").
     */
    val type: String,

    /**
     * SpEL expression to extract the target ID from the method arguments.
     * e.g., "#adId" or "#request.id".
     */
    val targetId: String = "",

    /**
     * The sample rate for this endpoint (0.0 to 1.0).
     * 1.0 means 100% of events are tracked.
     */
    val sampleRate: Double = 1.0
)
