package com.gimlee.notifications.persistence.model

import com.gimlee.notifications.domain.model.*
import org.bson.Document
import org.bson.types.ObjectId

data class NotificationDocument(
    val id: String,
    val userId: ObjectId,
    val type: String,
    val category: String,
    val severity: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val suggestedAction: SuggestedAction?,
    val metadata: Map<String, String>?,
    val createdAt: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_USER_ID = "uid"
        const val FIELD_TYPE = "t"
        const val FIELD_CATEGORY = "cat"
        const val FIELD_SEVERITY = "sev"
        const val FIELD_TITLE = "ti"
        const val FIELD_MESSAGE = "msg"
        const val FIELD_READ = "r"
        const val FIELD_SUGGESTED_ACTION = "sa"
        const val FIELD_SA_TYPE = "t"
        const val FIELD_SA_TARGET = "tg"
        const val FIELD_METADATA = "md"
        const val FIELD_CREATED_AT = "ca"

        @Suppress("UNCHECKED_CAST")
        fun fromDocument(doc: Document): NotificationDocument = NotificationDocument(
            id = doc.getString(FIELD_ID),
            userId = doc.getObjectId(FIELD_USER_ID),
            type = doc.getString(FIELD_TYPE),
            category = doc.getString(FIELD_CATEGORY),
            severity = doc.getString(FIELD_SEVERITY),
            title = doc.getString(FIELD_TITLE),
            message = doc.getString(FIELD_MESSAGE),
            read = doc.getBoolean(FIELD_READ, false),
            suggestedAction = doc.get(FIELD_SUGGESTED_ACTION, Document::class.java)?.let { sa ->
                SuggestedAction(
                    type = SuggestedActionType.fromShortName(sa.getString(FIELD_SA_TYPE)),
                    target = sa.getString(FIELD_SA_TARGET)
                )
            },
            metadata = doc.get(FIELD_METADATA, Document::class.java)?.let { md ->
                md.entries.associate { it.key to it.value.toString() }
            },
            createdAt = doc.getLong(FIELD_CREATED_AT)
        )
    }

    fun toBson(): Document {
        val bson = Document()
            .append(FIELD_ID, id)
            .append(FIELD_USER_ID, userId)
            .append(FIELD_TYPE, type)
            .append(FIELD_CATEGORY, category)
            .append(FIELD_SEVERITY, severity)
            .append(FIELD_TITLE, title)
            .append(FIELD_MESSAGE, message)
            .append(FIELD_READ, read)
            .append(FIELD_CREATED_AT, createdAt)
        suggestedAction?.let { sa ->
            bson.append(
                FIELD_SUGGESTED_ACTION, Document()
                    .append(FIELD_SA_TYPE, sa.type.shortName)
                    .append(FIELD_SA_TARGET, sa.target)
            )
        }
        metadata?.let { bson.append(FIELD_METADATA, Document(it)) }
        return bson
    }

    fun toDomain(): Notification = Notification(
        id = id,
        userId = userId.toHexString(),
        type = NotificationType.fromSlug(type),
        category = NotificationCategory.fromShortName(category),
        severity = NotificationSeverity.fromShortName(severity),
        title = title,
        message = message,
        read = read,
        suggestedAction = suggestedAction,
        metadata = metadata,
        createdAt = createdAt
    )
}
