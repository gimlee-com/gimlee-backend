package com.gimlee.user.domain

import com.gimlee.common.UUIDv7
import com.gimlee.user.domain.model.DeliveryAddress
import com.gimlee.user.persistence.DeliveryAddressRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class DeliveryAddressService(
    private val deliveryAddressRepository: DeliveryAddressRepository
) {
    companion object {
        const val MAX_ADDRESSES_PER_USER = 50
    }

    class MaxAddressesReachedException : RuntimeException("Maximum number of delivery addresses reached ($MAX_ADDRESSES_PER_USER)")

    fun addDeliveryAddress(
        userId: String,
        name: String,
        fullName: String,
        street: String,
        city: String,
        postalCode: String,
        country: String,
        phoneNumber: String,
        isDefault: Boolean
    ): DeliveryAddress {
        val currentCount = deliveryAddressRepository.countByUserId(userId)
        if (currentCount >= MAX_ADDRESSES_PER_USER) {
            throw MaxAddressesReachedException()
        }

        val now = Instant.now()
        
        if (isDefault) {
            val existingAddresses = deliveryAddressRepository.findAllByUserId(userId)
            existingAddresses.filter { it.isDefault }.forEach {
                deliveryAddressRepository.save(it.copy(isDefault = false, updatedAt = now))
            }
        }

        val address = DeliveryAddress(
            id = UUIDv7.generate(),
            userId = userId,
            name = name,
            fullName = fullName,
            street = street,
            city = city,
            postalCode = postalCode,
            country = country,
            phoneNumber = phoneNumber,
            isDefault = if (currentCount == 0L) true else isDefault,
            createdAt = now,
            updatedAt = now
        )

        return deliveryAddressRepository.save(address)
    }

    fun getDeliveryAddresses(userId: String): List<DeliveryAddress> {
        return deliveryAddressRepository.findAllByUserId(userId)
    }
}
