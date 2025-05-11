package com.gimlee.auth.persistence

import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import com.gimlee.auth.model.Role
import com.gimlee.auth.domain.UserRole
import com.gimlee.auth.domain.UserRole.Companion.FIELD_ROLE
import com.gimlee.auth.domain.UserRole.Companion.FIELD_USERID

@Component
class UserRoleRepository(
    private val mongoTemplate: MongoTemplate
) {

    companion object {
        const val USER_ROLES_COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-userRoles"
    }

    fun getAll(userId: ObjectId): List<Role> {
        val query = Query.query(
            Criteria.where(FIELD_USERID).`is`(userId)
        )

        return mongoTemplate.find(
            query,
            UserRole::class.java,
            USER_ROLES_COLLECTION_NAME
        ).map { it.role }
    }

    fun <T> findAllByField(fieldName: String, fieldValue: T): List<UserRole> {
        val query = Query.query(
            Criteria.where(fieldName).`is`(fieldValue)
        )
        return mongoTemplate.find(
            query,
            UserRole::class.java,
            USER_ROLES_COLLECTION_NAME
        )
    }


    fun add(userId: ObjectId, role: Role) = mongoTemplate.save(
        UserRole(userId, role),
        USER_ROLES_COLLECTION_NAME
    )

    fun remove(userId: ObjectId, role: Role) = mongoTemplate.remove(
        Query(Criteria
            .where(FIELD_USERID).`is`(userId)
            .and(FIELD_ROLE).`is`(role)),
        USER_ROLES_COLLECTION_NAME
    )
}