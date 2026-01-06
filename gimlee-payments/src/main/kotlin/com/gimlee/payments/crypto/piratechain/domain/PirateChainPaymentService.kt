package com.gimlee.payments.crypto.piratechain.domain
import com.gimlee.common.domain.model.Currency

import com.gimlee.payments.crypto.domain.CryptoPaymentService
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.piratechain.client.PirateChainRpcClient
import org.springframework.stereotype.Service

@Service
class PirateChainPaymentService(
    userWalletAddressRepository: UserWalletAddressRepository,
    pirateChainRpcClient: PirateChainRpcClient
) : CryptoPaymentService(
    userWalletAddressRepository,
    pirateChainRpcClient,
    Currency.ARRR
)