package com.gimlee.payments.crypto.ycash.domain
import com.gimlee.common.domain.model.Currency

import com.gimlee.payments.crypto.domain.CryptoPaymentService
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.ycash.client.YcashRpcClient
import org.springframework.stereotype.Service

@Service
class YcashPaymentService(
    userWalletAddressRepository: UserWalletAddressRepository,
    ycashRpcClient: YcashRpcClient
) : CryptoPaymentService(
    userWalletAddressRepository,
    ycashRpcClient,
    Currency.YEC
)
