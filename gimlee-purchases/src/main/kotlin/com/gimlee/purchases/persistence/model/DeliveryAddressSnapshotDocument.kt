package com.gimlee.purchases.persistence.model

data class DeliveryAddressSnapshotDocument(
    val name: String,
    val fullName: String,
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String,
    val phoneNumber: String
) {
    companion object {
        const val FIELD_NAME = "n"
        const val FIELD_FULL_NAME = "fn"
        const val FIELD_STREET = "str"
        const val FIELD_CITY = "ct"
        const val FIELD_POSTAL_CODE = "pc"
        const val FIELD_COUNTRY = "co"
        const val FIELD_PHONE_NUMBER = "ph"
    }
}
