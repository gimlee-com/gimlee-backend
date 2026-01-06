package com.gimlee.payments.crypto.piratechain.domain
import com.gimlee.common.domain.model.Currency

import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.persistence.PaymentRepository
import com.gimlee.payments.crypto.domain.CryptoPaymentMonitor
import com.gimlee.payments.crypto.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.crypto.piratechain.config.PirateChainClientConfig.Companion.PIRATE_CHAIN_MONITOR_EXECUTOR
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService

@Component
class PirateChainPaymentMonitor(
    paymentRepository: PaymentRepository,
    paymentService: PaymentService,
    pirateChainRpcClient: PirateChainRpcClient,
    paymentProperties: PaymentProperties,
    @Qualifier(PIRATE_CHAIN_MONITOR_EXECUTOR)
    executorService: ExecutorService
) : CryptoPaymentMonitor(
    paymentRepository,
    paymentService,
    pirateChainRpcClient,
    paymentProperties,
    executorService,
    Currency.ARRR,
    PaymentMethod.PIRATE_CHAIN
) {
    @Scheduled(fixedDelayString = "\${gimlee.payments.pirate-chain.monitor-delay-ms:10000}")
    override fun monitorPayments() {
        super.monitorPayments()
    }
}
