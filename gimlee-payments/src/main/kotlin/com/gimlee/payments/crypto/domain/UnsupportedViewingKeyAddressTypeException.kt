package com.gimlee.payments.crypto.domain

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.crypto.persistence.model.WalletShieldedAddressType

class UnsupportedViewingKeyAddressTypeException(
    val currency: Currency,
    val addressType: String,
    val supportedAddressTypes: Set<WalletShieldedAddressType>
) : RuntimeException(
    "Unsupported $currency address type imported from viewing key: $addressType. " +
        "Supported: ${supportedAddressTypes.joinToString(", ") { it.rpcName }}"
)
