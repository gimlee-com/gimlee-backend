package com.gimlee.payments.crypto.domain

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.crypto.client.CryptoClient
import com.gimlee.payments.crypto.client.model.ReceivedTransaction
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.Payment
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.persistence.PaymentRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

abstract class CryptoPaymentMonitor(
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val cryptoClient: CryptoClient,
    private val paymentProperties: PaymentProperties,
    private val executorService: ExecutorService,
    private val cryptoCurrency: Currency,
    private val paymentMethod: PaymentMethod
) {
    private val log = LoggerFactory.getLogger(javaClass)

    open fun monitorPayments() {
        log.info("Starting $cryptoCurrency payment monitoring cycle...")
        val activePayments = paymentRepository.findAllByStatus(PaymentStatus.AWAITING_CONFIRMATION)
            .filter { it.paymentMethod == paymentMethod }
            
        if (activePayments.isEmpty()) {
            log.info("No active $cryptoCurrency payments to monitor.")
            return
        }

        // Group by receiving address to minimize RPC calls
        val paymentsByAddress = activePayments
            .groupBy { it.receivingAddress }

        val futures = paymentsByAddress.map { (address, payments) ->
            CompletableFuture.runAsync({
                try {
                    processAddress(address, payments)
                } catch (e: Exception) {
                    log.error("Error monitoring address $address for $cryptoCurrency: ${e.message}", e)
                }
            }, executorService)
        }

        CompletableFuture.allOf(*futures.toTypedArray()).join()
        log.info("$cryptoCurrency payment monitoring cycle completed.")
    }

    private fun processAddress(address: String, payments: List<Payment>) {
        val minConfs = when (cryptoCurrency) {
            Currency.ARRR -> paymentProperties.pirateChain.minConfirmations
            Currency.YEC -> paymentProperties.ycash.minConfirmations
            else -> throw IllegalArgumentException("Currency $cryptoCurrency is not a settlement crypto currency")
        }
        val response = cryptoClient.getReceivedByAddress(address, minConfs)
        val transactions = response.result?.map { it.toReceivedTransaction() } ?: emptyList()

        payments.forEach { payment ->
            try {
                checkPayment(payment, transactions)
            } catch (e: Exception) {
                log.error("Error processing payment ${payment.id} for $cryptoCurrency: ${e.message}", e)
            }
        }
    }

    private fun checkPayment(payment: Payment, transactions: List<ReceivedTransaction>) {
        val matchingTxs = transactions.filter { tx ->
            tx.memo != null && tx.memo == payment.memo
        }

        val totalPaid = matchingTxs.sumOf { BigDecimal.valueOf(it.amount) }

        if (totalPaid >= payment.amount) {
            log.info("Payment ${payment.id} for $cryptoCurrency paid fully. Total paid: $totalPaid, Required: ${payment.amount}")
            paymentService.updatePaymentStatus(payment.id, PaymentStatus.COMPLETE, totalPaid)
        } else {
            val now = Instant.now()
            if (now.isAfter(payment.deadline)) {
                if (totalPaid > BigDecimal.ZERO) {
                     log.info("Payment ${payment.id} for $cryptoCurrency timed out with partial payment: $totalPaid")
                     paymentService.updatePaymentStatus(payment.id, PaymentStatus.COMPLETE_UNDERPAID, totalPaid)
                } else {
                     log.info("Payment ${payment.id} for $cryptoCurrency timed out with no payment.")
                     paymentService.updatePaymentStatus(payment.id, PaymentStatus.FAILED_SOFT_TIMEOUT, totalPaid)
                }
            } else {
                 if (totalPaid > BigDecimal.ZERO) {
                     log.debug("Payment ${payment.id} for $cryptoCurrency partially paid: $totalPaid / ${payment.amount}")
                     paymentService.updatePaymentStatus(payment.id, PaymentStatus.AWAITING_CONFIRMATION, totalPaid)
                 }
            }
        }
    }
}
