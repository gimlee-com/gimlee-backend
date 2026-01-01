package com.gimlee.user.persistence.model

import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import com.gimlee.user.domain.model.DeliveryAddress
import org.bson.types.ObjectId
import java.util.UUID

data class DeliveryAddressDocument(
    val id: UUID,
    val userId: ObjectId,
    val name: String,
    val fullName: String,
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String,
    val phoneNumber: String,
    val isDefault: Boolean,
    val createdAtMicros: Long,
    val updatedAtMicros: Long
) {
    fun toDomain(): DeliveryAddress = DeliveryAddress(
        id = id,
        userId = userId.toHexString(),
        name = name,
        fullName = fullName,
        street = street,
        city = city,
        postalCode = postalCode,
        country = country,
        phoneNumber = phoneNumber,
        isDefault = isDefault,
        createdAt = fromMicros(createdAtMicros),
        updatedAt = fromMicros(updatedAtMicros)
    )

    companion object {
        const val COLLECTION_NAME = "user-delivery-addresses"
        const val FIELD_ID = "_id"
        const val FIELD_USER_ID = "uid"
        const val FIELD_NAME = "n"
        const val FIELD_FULL_NAME = "fn"
        const val FIELD_STREET = "str"
        const val FIELD_CITY = "ct"
        const val FIELD_POSTAL_CODE = "pc"
        const val FIELD_COUNTRY = "co"
        const val FIELD_PHONE_NUMBER = "ph"
        const val FIELD_IS_DEFAULT = "def"
        const val FIELD_CREATED_AT = "crt"
        const val FIELD_UPDATED_AT = "upd"

        fun fromDomain(domain: DeliveryAddress): DeliveryAddressDocument = DeliveryAddressDocument(
            id = domain.id,
            userId = ObjectId(domain.userId),
            name = domain.name,
            fullName = domain.fullName,
            street = domain.street,
            city = domain.city,
            postalCode = domain.postalCode,
            country = domain.country,
            phoneNumber = domain.phoneNumber,
            isDefault = domain.isDefault,
            createdAtMicros = domain.createdAt.toMicros(),
            updatedAtMicros = domain.updatedAt.toMicros()
        )
    }
}
