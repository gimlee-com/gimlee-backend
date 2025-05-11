package com.gimlee.auth.persistence

import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import com.gimlee.auth.domain.UserVerificationCode
import com.gimlee.auth.domain.UserVerificationCode.Companion.FIELD_ISSUED_AT
import com.gimlee.auth.domain.UserVerificationCode.Companion.FIELD_USERID
import com.gimlee.auth.domain.UserVerificationCode.Companion.FIELD_VERIFICATION_CODE
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Component
class VerificationCodeRepository(
    private val mongoTemplate: MongoTemplate,
    @Value("\${gimlee.api.verification.code.validity.minutes:60}")
    private val verificationCodeValidityMinutes: Long
) {
    companion object {
        const val VERIFICATION_CODES_COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-userVerificationCodes"
    }

    fun add(userId: ObjectId, verificationCode: String) =
        mongoTemplate.save(
            UserVerificationCode(userId, verificationCode, LocalDateTime.now()),
            VERIFICATION_CODES_COLLECTION_NAME
        )

    fun existsAndIsNotExpired(userId: ObjectId, verificationCode: String): Boolean {
        val date = LocalDateTime.now().minus(verificationCodeValidityMinutes, ChronoUnit.MINUTES)

        val query = Query(
            Criteria
                .where(FIELD_USERID).`is`(userId)
                .and(FIELD_VERIFICATION_CODE).`is`(verificationCode)
                .and(FIELD_ISSUED_AT).gte(date)
        )

        val verificationCodeRecord = mongoTemplate.findAndRemove(
            query,
            UserVerificationCode::class.java,
            VERIFICATION_CODES_COLLECTION_NAME
        )

        return verificationCodeRecord != null
    }
}