package com.gimlee.mediastore.domain

import com.gimlee.common.domain.model.Outcome

enum class MediaStoreOutcome(override val httpCode: Int) : Outcome {
    FILE_NOT_FOUND(404);

    override val code: String get() = "MEDIA_$name"
    override val messageKey: String get() = "status.media.${name.replace("_", "-").lowercase()}"
}
