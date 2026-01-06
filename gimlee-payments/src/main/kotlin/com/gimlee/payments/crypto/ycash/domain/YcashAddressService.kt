package com.gimlee.payments.crypto.ycash.domain
import com.gimlee.common.domain.model.Currency

import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.payments.crypto.domain.CryptoAddressService
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.ycash.client.YcashRpcClient
import org.springframework.stereotype.Service

@Service
class YcashAddressService(
    userWalletAddressRepository: UserWalletAddressRepository,
    ycashRpcClient: YcashRpcClient,
    userRoleRepository: UserRoleRepository
) : CryptoAddressService(
    userWalletAddressRepository,
    ycashRpcClient,
    userRoleRepository,
    Currency.YEC,
    Role.YCASH
)
