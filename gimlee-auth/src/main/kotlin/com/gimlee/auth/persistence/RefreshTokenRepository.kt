package com.gimlee.auth.persistence

import com.gimlee.auth.domain.RefreshToken
import com.gimlee.common.InstantUtils
import com.gimlee.common.toMicros
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class RefreshTokenRepository(
    private val mongoTemplate: MongoTemplate
) {
    companion object {
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-refreshTokens"
    }

    fun save(token: RefreshToken) {
        val doc = Document()
            .append(RefreshToken.FIELD_ID, token.id.toString())
            .append(RefreshToken.FIELD_USER_ID, token.userId)
            .append(RefreshToken.FIELD_FAMILY_ID, token.familyId.toString())
            .append(RefreshToken.FIELD_HASHED_TOKEN, token.hashedToken)
            .append(RefreshToken.FIELD_DEVICE_ID, token.deviceId)
            .append(RefreshToken.FIELD_ISSUED_AT, token.issuedAt)
            .append(RefreshToken.FIELD_EXPIRES_AT, token.expiresAt)
            .append(RefreshToken.FIELD_REVOKED, token.revoked)
            .append(RefreshToken.FIELD_REVOKED_AT, token.revokedAt)
        mongoTemplate.save(doc, COLLECTION_NAME)
    }

    fun findByHashedToken(hashedToken: String): RefreshToken? {
        val query = Query(Criteria.where(RefreshToken.FIELD_HASHED_TOKEN).`is`(hashedToken))
        val doc = mongoTemplate.findOne(query, Document::class.java, COLLECTION_NAME) ?: return null
        return toRefreshToken(doc)
    }

    fun revokeByFamily(familyId: UUID) {
        val now = Instant.now().toMicros()
        val query = Query(
            Criteria.where(RefreshToken.FIELD_FAMILY_ID).`is`(familyId.toString())
                .and(RefreshToken.FIELD_REVOKED).`is`(false)
        )
        val update = Update()
            .set(RefreshToken.FIELD_REVOKED, true)
            .set(RefreshToken.FIELD_REVOKED_AT, now)
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME)
    }

    fun revokeAllForUser(userId: String) {
        val now = Instant.now().toMicros()
        val query = Query(
            Criteria.where(RefreshToken.FIELD_USER_ID).`is`(userId)
                .and(RefreshToken.FIELD_REVOKED).`is`(false)
        )
        val update = Update()
            .set(RefreshToken.FIELD_REVOKED, true)
            .set(RefreshToken.FIELD_REVOKED_AT, now)
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME)
    }

    fun deleteExpired(beforeMicros: Long): Long {
        val query = Query(
            Criteria.where(RefreshToken.FIELD_EXPIRES_AT).lt(beforeMicros)
        )
        return mongoTemplate.remove(query, COLLECTION_NAME).deletedCount
    }

    fun deleteRevokedBefore(beforeMicros: Long): Long {
        val query = Query(
            Criteria.where(RefreshToken.FIELD_REVOKED).`is`(true)
                .and(RefreshToken.FIELD_REVOKED_AT).lt(beforeMicros)
        )
        return mongoTemplate.remove(query, COLLECTION_NAME).deletedCount
    }

    fun findActiveSessionsByUser(userId: String): List<RefreshToken> {
        val now = Instant.now().toMicros()
        val query = Query(
            Criteria.where(RefreshToken.FIELD_USER_ID).`is`(userId)
                .and(RefreshToken.FIELD_REVOKED).`is`(false)
                .and(RefreshToken.FIELD_EXPIRES_AT).gt(now)
        )
        return mongoTemplate.find(query, Document::class.java, COLLECTION_NAME)
            .map { toRefreshToken(it) }
    }

    private fun toRefreshToken(doc: Document): RefreshToken {
        return RefreshToken(
            id = UUID.fromString(doc.getString(RefreshToken.FIELD_ID)),
            userId = doc.getString(RefreshToken.FIELD_USER_ID),
            familyId = UUID.fromString(doc.getString(RefreshToken.FIELD_FAMILY_ID)),
            hashedToken = doc.getString(RefreshToken.FIELD_HASHED_TOKEN),
            deviceId = doc.getString(RefreshToken.FIELD_DEVICE_ID),
            issuedAt = doc.getLong(RefreshToken.FIELD_ISSUED_AT),
            expiresAt = doc.getLong(RefreshToken.FIELD_EXPIRES_AT),
            revoked = doc.getBoolean(RefreshToken.FIELD_REVOKED),
            revokedAt = doc.getLong(RefreshToken.FIELD_REVOKED_AT)
        )
    }
}
