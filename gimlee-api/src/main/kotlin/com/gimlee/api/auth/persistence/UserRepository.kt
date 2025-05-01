package com.gimlee.api.auth.persistence

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import com.gimlee.api.auth.domain.User
import com.gimlee.api.auth.domain.User.Companion.FIELD_PASSWORD
import com.gimlee.api.auth.domain.User.Companion.FIELD_PASSWORD_SALT
import com.gimlee.api.auth.domain.User.Companion.FIELD_USERNAME
import com.gimlee.api.auth.domain.User.Companion.FIELD_VERIFICATION_CODE

@Component
class UserRepository(
    private val mongoTemplate: MongoTemplate
) {
    companion object {
        const val USERS_COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-users"
    }

    fun findOneByFields(vararg fields: Pair<String, Any>, includeCredentials: Boolean = false): User? {

        var criteria = Criteria.where(fields[0].first).`is`(fields[0].second)
        for (i in 1 until fields.size) {
            criteria = criteria.and(fields[i].first).`is`(fields[i].second)
        }

        val query = Query(criteria)

        if (!includeCredentials) {
            query.fields()
                .exclude(FIELD_PASSWORD)
                .exclude(FIELD_PASSWORD_SALT)
                .exclude(FIELD_VERIFICATION_CODE)
        }

        return mongoTemplate.findOne(query, User::class.java, USERS_COLLECTION_NAME)
    }

    fun findOneByField(field: String, value: Any, includeCredentials: Boolean = false): User? {
        return findOneByFields(Pair(field, value), includeCredentials = includeCredentials)
    }

    fun getUserHexSaltAndPasswordHash(username: String): User? {
        val query = Query.query(
            Criteria
                .where(FIELD_USERNAME).`is`(username)
        )
        query.fields()
            .include(FIELD_PASSWORD)
            .include(FIELD_PASSWORD_SALT)

        return mongoTemplate.findOne(query, User::class.java, USERS_COLLECTION_NAME)
    }

    fun save(user: User) = mongoTemplate.save(user, USERS_COLLECTION_NAME)
}