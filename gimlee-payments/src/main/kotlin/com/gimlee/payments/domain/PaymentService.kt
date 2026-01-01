package com.gimlee.payments.domain

import com.gimlee.events.PaymentEvent
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.domain.model.Payment
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.persistence.PaymentEventRepository
import com.gimlee.payments.persistence.PaymentRepository
import com.gimlee.payments.piratechain.persistence.UserPirateChainAddressRepository
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val userPirateChainAddressRepository: UserPirateChainAddressRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val paymentProperties: PaymentProperties
) {

    /**
     * Initializes a new payment process for a given order.
     * Creates the Payment entity, saves it, and publishes the initial event.
     */
    fun initPayment(
        orderId: ObjectId,
        buyerId: ObjectId,
        sellerId: ObjectId,
        amount: BigDecimal,
        paymentMethod: PaymentMethod
    ): Payment {
        
        val receivingAddress = if (paymentMethod == PaymentMethod.PIRATE_CHAIN) {
             val sellerAddresses = userPirateChainAddressRepository.findByUserId(sellerId)
             sellerAddresses?.addresses?.firstOrNull()?.zAddress
                ?: throw IllegalStateException("Seller $sellerId does not have a Pirate Chain address set up.")
        } else {
             throw UnsupportedOperationException("Payment method $paymentMethod not supported")
        }

        val now = Instant.now()
        val deadline = now.plus(paymentProperties.timeoutHours, ChronoUnit.HOURS)
        val memo = "${paymentProperties.pirateChain.memoPrefix}${orderId.toHexString()}"

        val payment = Payment(
            id = ObjectId.get(),
            orderId = orderId,
            buyerId = buyerId,
            sellerId = sellerId,
            amount = amount,
            status = PaymentStatus.AWAITING_CONFIRMATION,
            paymentMethod = paymentMethod,
            memo = memo,
            deadline = deadline,
            receivingAddress = receivingAddress,
            createdAt = now
        )

        paymentRepository.save(payment)

        publishEvent(payment)

        return payment
    }

    fun updatePaymentStatus(paymentId: ObjectId, newStatus: PaymentStatus) {
        val payment = paymentRepository.findById(paymentId) ?: return
        if (payment.status != newStatus) {
            val updatedPayment = payment.copy(status = newStatus)
            paymentRepository.save(updatedPayment)
            publishEvent(updatedPayment)
        }
    }

    fun getPaymentByOrderId(orderId: ObjectId): Payment? = paymentRepository.findByOrderId(orderId)
    
    private fun publishEvent(payment: Payment) {
        val paymentEvent = PaymentEvent(
            orderId = payment.orderId,
            buyerId = payment.buyerId,
            sellerId = payment.sellerId,
            status = payment.status.id,
            paymentMethod = payment.paymentMethod.id,
            amount = payment.amount,
            timestamp = Instant.now()
        )
        paymentEventRepository.save(paymentEvent)
        applicationEventPublisher.publishEvent(paymentEvent)
    }
}