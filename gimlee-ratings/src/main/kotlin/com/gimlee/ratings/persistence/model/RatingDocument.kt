package com.gimlee.ratings.persistence.model

import com.gimlee.ratings.domain.model.*

data class RatingDocument(
    val id: String,
    val contextType: String,
    val contextId: String,
    val subjectKind: String,
    val repKind: String,
    val raterId: String,
    val rateeId: String,
    val score: Int,
    val title: String? = null,
    val body: String? = null,
    val photoPaths: List<String>? = null,
    val snapshot: RatingSnapshotDocument? = null,
    val status: String,
    val edited: Boolean = false,
    val editableUntil: Long,
    val supplements: List<RatingSupplementDocument>? = null,
    val response: RatingResponseDocument? = null,
    val helpfulCount: Int = 0,
    val reportCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val publishedAt: Long? = null
) {
    companion object {
        const val COLLECTION_NAME = "gimlee-ratings-ratings"

        const val FIELD_ID = "_id"
        const val FIELD_CONTEXT_TYPE = "ct"
        const val FIELD_CONTEXT_ID = "cid"
        const val FIELD_SUBJECT_KIND = "sk"
        const val FIELD_REP_KIND = "rk"
        const val FIELD_RATER_ID = "rtr"
        const val FIELD_RATEE_ID = "rte"
        const val FIELD_SCORE = "sc"
        const val FIELD_TITLE = "ttl"
        const val FIELD_BODY = "bd"
        const val FIELD_PHOTO_PATHS = "ph"
        const val FIELD_SNAPSHOT = "snp"
        const val FIELD_STATUS = "st"
        const val FIELD_EDITED = "ed"
        const val FIELD_EDITABLE_UNTIL = "eu"
        const val FIELD_SUPPLEMENTS = "sup"
        const val FIELD_RESPONSE = "rsp"
        const val FIELD_HELPFUL_COUNT = "hc"
        const val FIELD_REPORT_COUNT = "rc"
        const val FIELD_CREATED_AT = "ca"
        const val FIELD_UPDATED_AT = "ua"
        const val FIELD_PUBLISHED_AT = "pa"

        fun fromDomain(domain: Rating): RatingDocument = RatingDocument(
            id = domain.id,
            contextType = domain.contextType,
            contextId = domain.contextId,
            subjectKind = domain.subjectKind.shortName,
            repKind = domain.repKind.shortName,
            raterId = domain.raterId,
            rateeId = domain.rateeId,
            score = domain.score,
            title = domain.title,
            body = domain.body,
            photoPaths = domain.photoPaths,
            snapshot = domain.snapshot?.let { RatingSnapshotDocument.fromDomain(it) },
            status = domain.status.shortName,
            edited = domain.edited,
            editableUntil = domain.editableUntil,
            supplements = domain.supplements?.map { RatingSupplementDocument.fromDomain(it) },
            response = domain.response?.let { RatingResponseDocument.fromDomain(it) },
            helpfulCount = domain.helpfulCount,
            reportCount = domain.reportCount,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            publishedAt = domain.publishedAt
        )
    }

    fun toDomain(): Rating = Rating(
        id = id,
        contextType = contextType,
        contextId = contextId,
        subjectKind = SubjectKind.fromShortName(subjectKind),
        repKind = RepKind.fromShortName(repKind),
        raterId = raterId,
        rateeId = rateeId,
        score = score,
        title = title,
        body = body,
        photoPaths = photoPaths,
        snapshot = snapshot?.toDomain(),
        status = RatingStatus.fromShortName(status),
        edited = edited,
        editableUntil = editableUntil,
        supplements = supplements?.map { it.toDomain() },
        response = response?.toDomain(),
        helpfulCount = helpfulCount,
        reportCount = reportCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        publishedAt = publishedAt
    )
}

data class RatingSnapshotDocument(
    val refType: String,
    val items: List<RatingSnapshotItemDocument>
) {
    companion object {
        const val FIELD_REF_TYPE = "rt"
        const val FIELD_ITEMS = "it"
        const val FIELD_ITEM_AD_ID = "aid"
        const val FIELD_ITEM_NAME = "nm"
        const val FIELD_ITEM_QUANTITY = "qty"
        const val FIELD_ITEM_UNIT_PRICE = "up"
        const val FIELD_ITEM_CURRENCY = "cur"
        const val FIELD_ITEM_THUMB_PATH = "tp"

        fun fromDomain(domain: RatingSubjectSnapshot): RatingSnapshotDocument = RatingSnapshotDocument(
            refType = domain.refType,
            items = domain.items.map { RatingSnapshotItemDocument.fromDomain(it) }
        )
    }

    fun toDomain(): RatingSubjectSnapshot = RatingSubjectSnapshot(
        refType = refType,
        items = items.map { it.toDomain() }
    )
}

data class RatingSnapshotItemDocument(
    val adId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: String,
    val currency: String,
    val thumbPath: String? = null
) {
    companion object {
        fun fromDomain(domain: RatingSnapshotItem): RatingSnapshotItemDocument = RatingSnapshotItemDocument(
            adId = domain.adId,
            name = domain.name,
            quantity = domain.quantity,
            unitPrice = domain.unitPrice,
            currency = domain.currency,
            thumbPath = domain.thumbPath
        )
    }

    fun toDomain(): RatingSnapshotItem = RatingSnapshotItem(
        adId = adId,
        name = name,
        quantity = quantity,
        unitPrice = unitPrice,
        currency = currency,
        thumbPath = thumbPath
    )
}

data class RatingSupplementDocument(
    val id: String,
    val body: String,
    val status: String,
    val editableUntil: Long,
    val createdAt: Long
) {
    companion object {
        const val FIELD_ID = "sid"
        const val FIELD_BODY = "bd"
        const val FIELD_STATUS = "st"
        const val FIELD_EDITABLE_UNTIL = "eu"
        const val FIELD_CREATED_AT = "ca"

        fun fromDomain(domain: RatingSupplement): RatingSupplementDocument = RatingSupplementDocument(
            id = domain.id,
            body = domain.body,
            status = domain.status.shortName,
            editableUntil = domain.editableUntil,
            createdAt = domain.createdAt
        )
    }

    fun toDomain(): RatingSupplement = RatingSupplement(
        id = id,
        body = body,
        status = RatingStatus.fromShortName(status),
        editableUntil = editableUntil,
        createdAt = createdAt
    )
}

data class RatingResponseDocument(
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val FIELD_BODY = "bd"
        const val FIELD_CREATED_AT = "ca"
        const val FIELD_UPDATED_AT = "ua"

        fun fromDomain(domain: RatingResponse): RatingResponseDocument = RatingResponseDocument(
            body = domain.body,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(): RatingResponse = RatingResponse(
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
