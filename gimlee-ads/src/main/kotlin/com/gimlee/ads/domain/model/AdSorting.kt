package com.gimlee.ads.domain.model

enum class By {
    CREATED_DATE,
}

enum class Direction {
    ASC, DESC
}

class AdSorting(
    val by: By,
    val direction: Direction
)