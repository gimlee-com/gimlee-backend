package com.gimlee.auth.persistence

import com.gimlee.auth.domain.UserBan
import com.gimlee.auth.domain.UserBan.Companion.FIELD_ACTIVE
import com.gimlee.auth.domain.UserBan.Companion.FIELD_BANNED_AT
import com.gimlee.auth.domain.UserBan.Companion.FIELD_BANNED_BY
import com.gimlee.auth.domain.UserBan.Companion.FIELD_BANNED_UNTIL
import com.gimlee.auth.domain.UserBan.Companion.FIELD_ID
import com.gimlee.auth.domain.UserBan.Companion.FIELD_REASON
import com.gimlee.auth.domain.UserBan.Companion.FIELD_UNBANNED_AT
import com.gimlee.auth.domain.UserBan.Companion.FIELD_UNBANNED_BY
import com.gimlee.auth.domain.UserBan.Companion.FIELD_USER_ID
import com.gimlee.common.InstantUtils
import com.gimlee.common.toMicros
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class UserBanRepository(
    private val mongoTemplate: MongoTemplate
) {

    companion object {
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-userBans"
    }

    fun save(ban: UserBan) {
        val doc = Document()
            .append(FIELD_ID, ban.id)
            .append(FIELD_USER_ID, ban.userId)
            .append(FIELD_REASON, ban.reason)
            .append(FIELD_BANNED_BY, ban.bannedBy)
            .append(FIELD_BANNED_AT, ban.bannedAt)
            .append(FIELD_BANNED_UNTIL, ban.bannedUntil)
            .append(FIELD_UNBANNED_BY, ban.unbannedBy)
            .append(FIELD_UNBANNED_AT, ban.unbannedAt)
            .append(FIELD_ACTIVE, ban.active)

        mongoTemplate.save(doc, COLLECTION_NAME)
    }

    fun findActiveByUserId(userId: String): UserBan? {
        val query = Query(
            Criteria.where(FIELD_USER_ID).`is`(userId)
                .and(FIELD_ACTIVE).`is`(true)
        )
        return mongoTemplate.findOne(query, Document::class.java, COLLECTION_NAME)
            ?.let { fromDocument(it) }
    }

    fun findAllByUserId(userId: String): List<UserBan> {
        val query = Query(
            Criteria.where(FIELD_USER_ID).`is`(userId)
        ).with(org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, FIELD_BANNED_AT
        ))
        return mongoTemplate.find(query, Document::class.java, COLLECTION_NAME)
            .map { fromDocument(it) }
    }

    fun deactivate(banId: String, unbannedBy: String) {
        val query = Query(Criteria.where(FIELD_ID).`is`(banId))
        val update = Update()
            .set(FIELD_ACTIVE, false)
            .set(FIELD_UNBANNED_BY, unbannedBy)
            .set(FIELD_UNBANNED_AT, Instant.now().toMicros())
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
    }

    fun findExpiredActiveBans(): List<UserBan> {
        val nowMicros = Instant.now().toMicros()
        val query = Query(
            Criteria.where(FIELD_ACTIVE).`is`(true)
                .and(FIELD_BANNED_UNTIL).ne(null).lte(nowMicros)
        )
        return mongoTemplate.find(query, Document::class.java, COLLECTION_NAME)
            .map { fromDocument(it) }
    }

    private fun fromDocument(doc: Document): UserBan = UserBan(
        id = doc.getString(FIELD_ID),
        userId = doc.getString(FIELD_USER_ID),
        reason = doc.getString(FIELD_REASON),
        bannedBy = doc.getString(FIELD_BANNED_BY),
        bannedAt = doc.getLong(FIELD_BANNED_AT),
        bannedUntil = doc.getLong(FIELD_BANNED_UNTIL),
        unbannedBy = doc.getString(FIELD_UNBANNED_BY),
        unbannedAt = doc.getLong(FIELD_UNBANNED_AT),
        active = doc.getBoolean(FIELD_ACTIVE)
    )
}
