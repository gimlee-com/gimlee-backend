package com.gimlee.payments.crypto.ycash.domain
import com.gimlee.common.domain.model.Currency

import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.persistence.PaymentRepository
import com.gimlee.payments.crypto.domain.CryptoPaymentMonitor
import com.gimlee.payments.crypto.ycash.client.YcashRpcClient
import com.gimlee.payments.crypto.ycash.config.YcashClientConfig.Companion.YCASH_MONITOR_EXECUTOR
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService

@Component
class YcashPaymentMonitor(
    paymentRepository: PaymentRepository,
    paymentService: PaymentService,
    ycashRpcClient: YcashRpcClient,
    paymentProperties: PaymentProperties,
    @Qualifier(YCASH_MONITOR_EXECUTOR)
    executorService: ExecutorService
) : CryptoPaymentMonitor(
    paymentRepository,
    paymentService,
    ycashRpcClient,
    paymentProperties,
    executorService,
    Currency.YEC,
    PaymentMethod.YCASH
) {
    @Scheduled(fixedDelayString = "\${gimlee.payments.ycash.monitor-delay-ms:10000}")
    override fun monitorPayments() {
        super.monitorPayments()
    }
}
