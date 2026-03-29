package com.gimlee.api.web.admin

import com.gimlee.auth.domain.User
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.support.report.domain.model.ReportTargetInfo
import com.gimlee.support.report.domain.model.ReportTargetResolver
import com.gimlee.support.report.domain.model.ReportTargetType
import com.gimlee.user.domain.ProfileService
import org.bson.types.ObjectId
import org.springframework.stereotype.Component

@Component
class UserReportTargetResolver(
    private val userRepository: UserRepository,
    private val profileService: ProfileService
) : ReportTargetResolver {

    override fun supports(targetType: ReportTargetType) = targetType == ReportTargetType.USER

    override fun resolve(targetType: ReportTargetType, targetId: String): ReportTargetInfo? {
        val user = userRepository.findOneByField(User.FIELD_ID, ObjectId(targetId)) ?: return null
        val profile = try { profileService.getProfile(targetId) } catch (_: Exception) { null }
        return ReportTargetInfo(
            targetId = targetId,
            targetType = ReportTargetType.USER,
            contextId = null,
            targetTitle = user.username ?: "Unknown",
            snapshot = mapOf(
                "username" to user.username,
                "displayName" to user.displayName,
                "avatarUrl" to profile?.avatarUrl,
                "status" to user.status?.name
            )
        )
    }
}
