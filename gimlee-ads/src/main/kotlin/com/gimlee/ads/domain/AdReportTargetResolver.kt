package com.gimlee.ads.domain

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.support.report.domain.model.ReportTargetInfo
import com.gimlee.support.report.domain.model.ReportTargetResolver
import com.gimlee.support.report.domain.model.ReportTargetType
import org.bson.types.ObjectId
import org.springframework.stereotype.Component

@Component
class AdReportTargetResolver(
    private val adRepository: AdRepository
) : ReportTargetResolver {

    override fun supports(targetType: ReportTargetType) = targetType == ReportTargetType.AD

    override fun resolve(targetType: ReportTargetType, targetId: String): ReportTargetInfo? {
        val ad = adRepository.findById(ObjectId(targetId))?.toDomain() ?: return null
        return ReportTargetInfo(
            targetId = ad.id,
            targetType = ReportTargetType.AD,
            contextId = null,
            targetTitle = ad.title,
            snapshot = mapOf(
                "title" to ad.title,
                "description" to ad.description,
                "price" to ad.price?.amount?.toPlainString(),
                "currency" to ad.price?.currency?.name,
                "status" to ad.status.name,
                "mediaPaths" to ad.mediaPaths,
                "userId" to ad.userId
            )
        )
    }
}
