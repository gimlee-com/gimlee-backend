package com.gimlee.ads.domain.model

data class Category(
    val id: Int,
    val source: Source,
    val parent: Int? = null,
    val flags: Map<String, Boolean> = emptyMap(),
    val name: Map<String, CategoryName> = emptyMap(),
    val popularity: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val children: List<Category> = emptyList()
) {
    data class Source(
        val type: Type,
        val id: String
    ) {
        enum class Type(val shortName: String) {
            GOOGLE_PRODUCT_TAXONOMY("GPT"),
            GIMLEE("GML");

            companion object {
                fun fromShortName(shortName: String): Type =
                    entries.find { it.shortName == shortName }
                        ?: throw IllegalArgumentException("Unknown Category.Source.Type shortName: $shortName")
            }
        }
    }

    data class CategoryName(
        val name: String,
        val slug: String
    )
}

