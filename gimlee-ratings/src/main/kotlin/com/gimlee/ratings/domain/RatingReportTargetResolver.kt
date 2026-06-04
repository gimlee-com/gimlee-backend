package com.gimlee.ratings.domain

import com.gimlee.ratings.persistence.RatingRepository
import com.gimlee.support.report.domain.model.ReportTargetInfo
import com.gimlee.support.report.domain.model.ReportTargetResolver
import com.gimlee.support.report.domain.model.ReportTargetType
import org.springframework.stereotype.Component

@Component
class RatingReportTargetResolver(
    private val ratingRepository: RatingRepository
) : ReportTargetResolver {

    override fun supports(targetType: ReportTargetType) = targetType == ReportTargetType.RATING

    override fun resolve(targetType: ReportTargetType, targetId: String): ReportTargetInfo? {
        val rating = ratingRepository.findById(targetId) ?: return null
        return ReportTargetInfo(
            targetId = rating.id,
            targetType = ReportTargetType.RATING,
            contextId = rating.contextId,
            targetTitle = "${rating.score}★ review by ${rating.raterId}",
            snapshot = mapOf(
                "score" to rating.score,
                "title" to rating.title,
                "body" to rating.body,
                "photoPaths" to rating.photoPaths,
                "raterId" to rating.raterId,
                "rateeId" to rating.rateeId,
                "contextType" to rating.contextType,
                "contextId" to rating.contextId,
                "status" to rating.status,
                "reportCount" to rating.reportCount,
                "subjectSnapshot" to (rating.snapshot?.let { snap ->
                    mapOf(
                        "refType" to snap.refType,
                        "items" to snap.items.map { item ->
                            mapOf(
                                "adId" to item.adId,
                                "name" to item.name,
                                "quantity" to item.quantity,
                                "unitPrice" to item.unitPrice,
                                "currency" to item.currency
                            )
                        }
                    )
                })
            )
        )
    }
}
