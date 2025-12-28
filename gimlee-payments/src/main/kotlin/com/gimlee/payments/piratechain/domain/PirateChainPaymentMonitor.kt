package com.gimlee.payments.piratechain.domain

import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.Payment
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.persistence.PaymentRepository
import com.gimlee.payments.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.piratechain.client.model.ReceivedTransaction
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant

@Component
class PirateChainPaymentMonitor(
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val pirateChainRpcClient: PirateChainRpcClient,
    private val paymentProperties: PaymentProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${gimlee.payments.pirate-chain.monitor-delay-ms:60000}")
    fun monitorPayments() {
        log.info("Starting payment monitoring cycle...")
        val activePayments = paymentRepository.findAllByStatus(PaymentStatus.AWAITING_CONFIRMATION)
        if (activePayments.isEmpty()) {
            log.info("No active payments to monitor.")
            return
        }

        // Group by receiving address to minimize RPC calls
        val paymentsByAddress = activePayments
            .groupBy { it.receivingAddress }

        paymentsByAddress.forEach { (address, payments) ->
            try {
                processAddress(address, payments)
            } catch (e: Exception) {
                log.error("Error monitoring address $address: ${e.message}", e)
            }
        }
        log.info("Payment monitoring cycle completed.")
    }

    private fun processAddress(address: String, payments: List<Payment>) {
        val response = pirateChainRpcClient.getReceivedByAddress(address, paymentProperties.pirateChain.minConfirmations)
        val transactions = response.result?.map { it.toReceivedTransaction() } ?: emptyList()

        payments.forEach { payment ->
            try {
                checkPayment(payment, transactions)
            } catch (e: Exception) {
                log.error("Error processing payment ${payment.id}: ${e.message}", e)
            }
        }
    }

    private fun checkPayment(payment: Payment, transactions: List<ReceivedTransaction>) {
        val matchingTxs = transactions.filter { tx ->
            tx.memo != null && tx.memo == payment.memo
        }

        val totalPaid = matchingTxs.sumOf { BigDecimal.valueOf(it.amount) }

        if (totalPaid >= payment.amount) {
            log.info("Payment ${payment.id} paid fully. Total paid: $totalPaid, Required: ${payment.amount}")
            paymentService.updatePaymentStatus(payment.id, PaymentStatus.COMPLETE)
        } else {
            val now = Instant.now()
            if (now.isAfter(payment.deadline)) {
                if (totalPaid > BigDecimal.ZERO) {
                     log.info("Payment ${payment.id} timed out with partial payment: $totalPaid")
                     paymentService.updatePaymentStatus(payment.id, PaymentStatus.COMPLETE_UNDERPAID)
                } else {
                     log.info("Payment ${payment.id} timed out with no payment.")
                     paymentService.updatePaymentStatus(payment.id, PaymentStatus.FAILED_SOFT_TIMEOUT)
                }
            } else {
                 if (totalPaid > BigDecimal.ZERO) {
                     log.debug("Payment ${payment.id} partially paid: $totalPaid / ${payment.amount}")
                 }
            }
        }
    }
}
