package com.gimlee.api.auth.service.user

import org.apache.commons.codec.binary.Hex
import org.bson.types.ObjectId
import org.springframework.stereotype.Service
import com.gimlee.auth.util.createHexPasswordHash
import com.gimlee.api.auth.persistence.UserRepository
import com.gimlee.api.auth.domain.User.Companion.FIELD_ID
import com.gimlee.api.domain.StatusCode

@Service
class ChangePasswordService(
    private val userRepository: UserRepository
) {
    fun changePassword(userId: String, oldPassword: String, newPassword: String): StatusCode {
        val user = userRepository.findOneByField(FIELD_ID, ObjectId(userId), includeCredentials = true)!!
        return if (createHexPasswordHash(oldPassword, Hex.decodeHex(user.passwordSalt)) == user.password) {
            userRepository.save(
                user.copy(password = createHexPasswordHash(newPassword, Hex.decodeHex(user.passwordSalt)))
            )
            StatusCode.SUCCESS
        } else {
            StatusCode.INCORRECT_CREDENTIALS
        }
    }
}