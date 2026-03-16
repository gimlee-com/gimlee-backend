package com.gimlee.ads.domain

import com.gimlee.common.domain.model.Outcome

enum class CategoryOutcome(override val httpCode: Int) : Outcome {
    CATEGORY_CREATED(201),
    CATEGORY_UPDATED(200),
    CATEGORY_DELETED(200),
    CATEGORY_MOVED(200),
    CATEGORY_REORDERED(200),
    CATEGORY_HIDDEN(200),
    CATEGORY_NOT_FOUND(404),
    CATEGORY_HAS_CHILDREN(409),
    CATEGORY_HAS_ADS(409),
    CATEGORY_HAS_ACTIVE_ADS(409),
    CATEGORY_DELETE_GPT_FORBIDDEN(400),
    CATEGORY_SLUG_DUPLICATE(409),
    CATEGORY_CIRCULAR_PARENT(400),
    CATEGORY_INVALID_PARENT(400),
    CATEGORY_ALREADY_AT_BOUNDARY(400);

    override val code: String get() = name
    override val messageKey: String get() = "status.category.${name.removePrefix("CATEGORY_").replace("_", "-").lowercase()}"
}
