package com.gimlee.chat.domain

import com.gimlee.chat.persistence.ChatRepository
import com.gimlee.support.report.domain.model.ReportTargetInfo
import com.gimlee.support.report.domain.model.ReportTargetResolver
import com.gimlee.support.report.domain.model.ReportTargetType
import org.springframework.stereotype.Component

@Component
class MessageReportTargetResolver(
    private val chatRepository: ChatRepository
) : ReportTargetResolver {

    override fun supports(targetType: ReportTargetType) = targetType == ReportTargetType.MESSAGE

    override fun resolve(targetType: ReportTargetType, targetId: String): ReportTargetInfo? {
        val message = chatRepository.findMessageById(targetId) ?: return null
        return ReportTargetInfo(
            targetId = message.id,
            targetType = ReportTargetType.MESSAGE,
            contextId = message.chatId,
            targetTitle = message.text.take(100),
            snapshot = mapOf(
                "chatId" to message.chatId,
                "text" to message.text,
                "author" to message.author,
                "timestamp" to message.timestamp.toEpochMilli()
            )
        )
    }
}
