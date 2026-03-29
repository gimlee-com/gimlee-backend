package com.gimlee.auth.persistence

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.User.Companion.FIELD_PASSWORD
import com.gimlee.auth.domain.User.Companion.FIELD_PASSWORD_SALT
import com.gimlee.auth.domain.User.Companion.FIELD_USERNAME
import com.gimlee.auth.domain.User.Companion.FIELD_VERIFICATION_CODE
import com.gimlee.auth.domain.UserRole
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.persistence.UserRoleRepository.Companion.USER_ROLES_COLLECTION_NAME
import org.bson.types.ObjectId

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

    fun findAllByIds(ids: Collection<ObjectId>, includeCredentials: Boolean = false): List<User> {
        val query = Query(Criteria.where(User.FIELD_ID).`in`(ids))
        if (!includeCredentials) {
            query.fields()
                .exclude(FIELD_PASSWORD)
                .exclude(FIELD_PASSWORD_SALT)
                .exclude(FIELD_VERIFICATION_CODE)
        }
        return mongoTemplate.find(query, User::class.java, USERS_COLLECTION_NAME)
    }

    fun findAll(): List<User> =
        mongoTemplate.findAll(User::class.java, USERS_COLLECTION_NAME)

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

    fun updateStatus(userId: ObjectId, status: UserStatus) {
        val query = Query(Criteria.where(User.FIELD_ID).`is`(userId))
        val update = org.springframework.data.mongodb.core.query.Update()
            .set("status", status)
        mongoTemplate.updateFirst(query, update, USERS_COLLECTION_NAME)
    }

    fun findAllPaginated(
        search: String?,
        status: UserStatus?,
        role: String?,
        sortField: String?,
        sortDirection: String?,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<User> {
        val criteria = mutableListOf<Criteria>()

        if (!search.isNullOrBlank()) {
            val regex = ".*${Regex.escape(search)}.*"
            criteria.add(Criteria().orOperator(
                Criteria.where(FIELD_USERNAME).regex(regex, "i"),
                Criteria.where(User.FIELD_EMAIL).regex(regex, "i")
            ))
        }

        if (status != null) {
            criteria.add(Criteria.where("status").`is`(status))
        }

        if (role != null) {
            val roleQuery = Query(Criteria.where(UserRole.FIELD_ROLE).`is`(role))
            val userIds = mongoTemplate.find(roleQuery, UserRole::class.java, USER_ROLES_COLLECTION_NAME)
                .map { it.userId }
            criteria.add(Criteria.where(User.FIELD_ID).`in`(userIds))
        }

        val query = if (criteria.isEmpty()) Query() else Query(Criteria().andOperator(criteria))
        query.fields()
            .exclude(FIELD_PASSWORD)
            .exclude(FIELD_PASSWORD_SALT)
            .exclude(FIELD_VERIFICATION_CODE)

        val total = mongoTemplate.count(query, USERS_COLLECTION_NAME)

        val sort = when (sortField) {
            "username" -> org.springframework.data.domain.Sort.by(
                if (sortDirection == "ASC") org.springframework.data.domain.Sort.Direction.ASC
                else org.springframework.data.domain.Sort.Direction.DESC,
                FIELD_USERNAME
            )
            "lastLogin" -> org.springframework.data.domain.Sort.by(
                if (sortDirection == "ASC") org.springframework.data.domain.Sort.Direction.ASC
                else org.springframework.data.domain.Sort.Direction.DESC,
                "lastLogin"
            )
            else -> org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, User.FIELD_ID
            )
        }

        query.with(sort).with(pageable)

        val users = mongoTemplate.find(query, User::class.java, USERS_COLLECTION_NAME)
        return org.springframework.data.domain.PageImpl(users, pageable, total)
    }
}