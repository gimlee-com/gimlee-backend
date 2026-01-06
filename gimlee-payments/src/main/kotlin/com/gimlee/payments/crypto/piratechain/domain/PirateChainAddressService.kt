package com.gimlee.payments.crypto.piratechain.domain
import com.gimlee.common.domain.model.Currency

import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.payments.crypto.domain.CryptoAddressService
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.piratechain.client.PirateChainRpcClient
import org.springframework.stereotype.Service

@Service
class PirateChainAddressService(
    userWalletAddressRepository: UserWalletAddressRepository,
    pirateChainRpcClient: PirateChainRpcClient,
    userRoleRepository: UserRoleRepository
) : CryptoAddressService(
    userWalletAddressRepository,
    pirateChainRpcClient,
    userRoleRepository,
    Currency.ARRR,
    Role.PIRATE
)
