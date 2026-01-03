package com.gimlee.payments.persistence

import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_AMOUNT
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_BUYER_ID
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_CREATED_AT
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_DEADLINE
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_ID
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_MEMO
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_PAYMENT_METHOD
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_PURCHASE_ID
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_RECEIVING_ADDRESS
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_SELLER_ID
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_STATUS
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCursor
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import java.math.BigDecimal

class PaymentRepositoryTest : StringSpec({
    val mongoDatabase = mockk<MongoDatabase>()
    val mongoCollection = mockk<MongoCollection<Document>>()
    val findIterable = mockk<FindIterable<Document>>()

    every { mongoDatabase.getCollection(any()) } returns mongoCollection
    
    val repository = PaymentRepository(mongoDatabase)

    "should default to paidAmount of BigDecimalZERO when the field is missing" {
        val id = ObjectId.get()
        val doc = Document()
            .append(FIELD_ID, id)
            .append(FIELD_PURCHASE_ID, ObjectId.get())
            .append(FIELD_BUYER_ID, ObjectId.get())
            .append(FIELD_SELLER_ID, ObjectId.get())
            .append(FIELD_AMOUNT, Decimal128(BigDecimal("10.0")))
            // FIELD_PAID_AMOUNT is missing
            .append(FIELD_STATUS, PaymentStatus.AWAITING_CONFIRMATION.id)
            .append(FIELD_PAYMENT_METHOD, PaymentMethod.PIRATE_CHAIN.id)
            .append(FIELD_MEMO, "memo")
            .append(FIELD_DEADLINE, 123456789L)
            .append(FIELD_RECEIVING_ADDRESS, "address")
            .append(FIELD_CREATED_AT, 123456000L)

        val cursor = mockk<MongoCursor<Document>>()
        every { mongoCollection.find(any<Bson>()) } returns findIterable
        every { findIterable.iterator() } returns cursor
        every { cursor.hasNext() } returns true andThen false
        every { cursor.next() } returns doc

        val payment = repository.findById(id)
        
        payment shouldNotBe null
        payment!!.paidAmount.stripTrailingZeros() shouldBe BigDecimal.ZERO.stripTrailingZeros()
    }
})
