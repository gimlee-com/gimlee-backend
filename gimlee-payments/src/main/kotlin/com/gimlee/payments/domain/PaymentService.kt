package com.gimlee.payments.domain

import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import com.gimlee.events.PaymentEvent
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.persistence.PaymentEventRepository
import java.math.BigDecimal
import java.time.Instant

@Service
class PaymentService(
    private val paymentEventRepository: PaymentEventRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    /**
     * Initializes a new payment process for a given order.
     * Creates the initial PaymentEvent, saves it to the repository,
     * and publishes it for other components to react.
     *
     * @param orderId The ID of the order the payment is for.
     * @param buyerId The ID of the user initiating the payment (buyer).
     * @param sellerId The ID of the user receiving the payment (seller).
     * @param amount The amount to be paid.
     * @param paymentMethod The selected payment method.
     * @return The created PaymentEvent representing the initial state.
     */
    fun initPayment(
        orderId: ObjectId,
        buyerId: ObjectId,
        sellerId: ObjectId,
        amount: BigDecimal,
        paymentMethod: PaymentMethod
    ): PaymentEvent {
        val paymentEvent = PaymentEvent(
            orderId = orderId,
            buyerId = buyerId,
            sellerId = sellerId,
            status = PaymentStatus.CREATED.id,
            paymentMethod = paymentMethod.id,
            amount = amount,
            timestamp = Instant.now()
        )

        paymentEventRepository.save(paymentEvent)
        applicationEventPublisher.publishEvent(paymentEvent)

        return paymentEvent
    }
}