package com.gimlee.payments.domain

import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.domain.model.Payment
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.persistence.PaymentRepository
import com.gimlee.payments.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.piratechain.client.model.RawReceivedTransaction
import com.gimlee.payments.piratechain.client.model.RpcResponse
import com.gimlee.payments.piratechain.domain.PirateChainPaymentMonitor
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

class PirateChainPaymentMonitorTest : StringSpec({

    val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    val paymentService = mockk<PaymentService>(relaxed = true)
    val rpcClient = mockk<PirateChainRpcClient>(relaxed = true)
    val paymentProperties = PaymentProperties(timeoutHours = 1)
    val executorService = Executors.newSingleThreadExecutor()
    val monitor = PirateChainPaymentMonitor(paymentRepository, paymentService, rpcClient, paymentProperties, executorService)

    "should complete payment when full payment is received" {
        val paymentId = ObjectId.get()
        val orderId = ObjectId.get()
        val payment = Payment(
            id = paymentId,
            orderId = orderId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            amount = BigDecimal("10.0"),
            status = PaymentStatus.AWAITING_CONFIRMATION,
            paymentMethod = PaymentMethod.PIRATE_CHAIN,
            memo = "gimlee:${orderId.toHexString()}",
            deadline = Instant.now().plus(1, ChronoUnit.HOURS),
            receivingAddress = "zs1seller",
            createdAt = Instant.now()
        )

        every { paymentRepository.findAllByStatus(PaymentStatus.AWAITING_CONFIRMATION) } returns listOf(payment)
        
        val memoString = "gimlee:${orderId.toHexString()}"
        val memoHex = memoString.toByteArray().joinToString("") { "%02x".format(it) }
        
        val tx = RawReceivedTransaction(
            txid = "tx1",
            memo = memoHex,
            amount = 10.0,
            confirmations = 10
        )
        
        every { rpcClient.getReceivedByAddress("zs1seller", 1) } returns RpcResponse(result = listOf(tx), error = null, id = "1")

        monitor.monitorPayments()

        verify { paymentService.updatePaymentStatus(paymentId, PaymentStatus.COMPLETE) }
    }

    "should mark as underpaid if deadline passed and partial payment" {
        val paymentId = ObjectId.get()
        val orderId = ObjectId.get()
        val payment = Payment(
            id = paymentId,
            orderId = orderId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            amount = BigDecimal("10.0"),
            status = PaymentStatus.AWAITING_CONFIRMATION,
            paymentMethod = PaymentMethod.PIRATE_CHAIN,
            memo = "gimlee:${orderId.toHexString()}",
            deadline = Instant.now().minus(1, ChronoUnit.HOURS),
            receivingAddress = "zs1seller",
            createdAt = Instant.now().minus(2, ChronoUnit.HOURS)
        )

        every { paymentRepository.findAllByStatus(PaymentStatus.AWAITING_CONFIRMATION) } returns listOf(payment)
        
        val memoString = "gimlee:${orderId.toHexString()}"
        val memoHex = memoString.toByteArray().joinToString("") { "%02x".format(it) }
        
        val tx = RawReceivedTransaction(
            txid = "tx1",
            memo = memoHex,
            amount = 5.0,
            confirmations = 10
        )
        
        every { rpcClient.getReceivedByAddress("zs1seller", 1) } returns RpcResponse(result = listOf(tx), error = null, id = "1")

        monitor.monitorPayments()

        verify { paymentService.updatePaymentStatus(paymentId, PaymentStatus.COMPLETE_UNDERPAID) }
    }
    
    "should mark as timeout if deadline passed and no payment" {
        val paymentId = ObjectId.get()
        val orderId = ObjectId.get()
        val payment = Payment(
            id = paymentId,
            orderId = orderId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            amount = BigDecimal("10.0"),
            status = PaymentStatus.AWAITING_CONFIRMATION,
            paymentMethod = PaymentMethod.PIRATE_CHAIN,
            memo = "gimlee:${orderId.toHexString()}",
            deadline = Instant.now().minus(1, ChronoUnit.HOURS),
            receivingAddress = "zs1seller",
            createdAt = Instant.now().minus(2, ChronoUnit.HOURS)
        )

        every { paymentRepository.findAllByStatus(PaymentStatus.AWAITING_CONFIRMATION) } returns listOf(payment)
        
        every { rpcClient.getReceivedByAddress("zs1seller", 1) } returns RpcResponse(result = emptyList(), error = null, id = "1")

        monitor.monitorPayments()

        verify { paymentService.updatePaymentStatus(paymentId, PaymentStatus.FAILED_SOFT_TIMEOUT) }
    }
})
