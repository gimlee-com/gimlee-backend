package com.gimlee.user.web.dto.response

import com.gimlee.user.domain.model.DeliveryAddress
import java.util.UUID

data class DeliveryAddressDto(
    val id: UUID,
    val name: String,
    val fullName: String,
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String,
    val phoneNumber: String,
    val isDefault: Boolean
) {
    companion object {
        fun fromDomain(domain: DeliveryAddress) = DeliveryAddressDto(
            id = domain.id,
            name = domain.name,
            fullName = domain.fullName,
            street = domain.street,
            city = domain.city,
            postalCode = domain.postalCode,
            country = domain.country,
            phoneNumber = domain.phoneNumber,
            isDefault = domain.isDefault
        )
    }
}
