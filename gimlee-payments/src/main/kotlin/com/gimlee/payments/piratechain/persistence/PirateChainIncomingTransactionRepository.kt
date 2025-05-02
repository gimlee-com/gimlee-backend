package com.gimlee.payments.piratechain.persistence

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Sorts
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument.Companion.FIELD_ADDRESS
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument.Companion.FIELD_AMOUNT
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument.Companion.FIELD_CONFIRMATIONS
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument.Companion.FIELD_DETECTED_AT
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument.Companion.FIELD_ID
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument.Companion.FIELD_MEMO
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument.Companion.FIELD_TXID
import com.gimlee.payments.piratechain.persistence.model.PirateChainIncomingTransactionDocument.Companion.FIELD_USER_ID

@Repository
class PirateChainIncomingTransactionRepository(
    private val mongoDatabase: MongoDatabase
) {
    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-payments"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-incoming-transactions"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    /**
     * Saves a transaction by converting it to a Document and inserting.
     */
    fun save(transaction: PirateChainIncomingTransactionDocument): PirateChainIncomingTransactionDocument {
        val doc = mapToDocument(transaction)
        collection.insertOne(doc)
        return transaction
    }

    /**
     * Finds transactions using a raw query document.
     */
    fun findByUserId(userId: ObjectId): List<PirateChainIncomingTransactionDocument> {
        val query = eq(FIELD_USER_ID, userId) // Use Filters.eq
        return collection.find(query)
            .sort(Sorts.descending(FIELD_DETECTED_AT))
            .map { mapToTransaction(it) } // Manual mapping
            .toList()
    }

    /**
     * Finds transactions using a compound raw query.
     */
    fun findByUserIdAndZAddress(userId: ObjectId, zAddress: String): List<PirateChainIncomingTransactionDocument> {
        val query = and(
            eq(FIELD_USER_ID, userId),
            eq(FIELD_ADDRESS, zAddress)
        )
        return collection.find(query)
            .sort(Sorts.descending(FIELD_DETECTED_AT))
            .map { mapToTransaction(it) }
            .toList()
    }

    /**
     * Finds a single transaction by txid.
     */
    fun findByTxid(txid: String): PirateChainIncomingTransactionDocument? {
        val query = eq(FIELD_TXID, txid)
        return collection.find(query)
            .limit(1)
            .firstOrNull()
            ?.let { mapToTransaction(it) }
    }

    /**
     * Checks existence using countDocuments.
     */
    fun exists(userId: ObjectId, zAddress: String, txid: String): Boolean {
        val query = and(
            eq(FIELD_USER_ID, userId),
            eq(FIELD_ADDRESS, zAddress),
            eq(FIELD_TXID, txid)
        )
        return collection.countDocuments(query) > 0
    }

    private fun mapToDocument(transaction: PirateChainIncomingTransactionDocument): Document {
        return Document()
            .append(FIELD_ID, transaction.id)
            .append(FIELD_USER_ID, transaction.userId)
            .append(FIELD_ADDRESS, transaction.address)
            .append(FIELD_TXID, transaction.txid)
            .append(FIELD_MEMO, transaction.memo) // Handles null automatically
            .append(FIELD_AMOUNT, transaction.amount)
            .append(FIELD_CONFIRMATIONS, transaction.confirmations)
            .append(FIELD_DETECTED_AT, transaction.detectedAtMicros)
    }

    private fun mapToTransaction(doc: Document): PirateChainIncomingTransactionDocument {
        return PirateChainIncomingTransactionDocument(
            id = doc.getObjectId(FIELD_ID),
            userId = doc.getObjectId(FIELD_USER_ID),
            address = doc.getString(FIELD_ADDRESS),
            txid = doc.getString(FIELD_TXID),
            memo = doc.getString(FIELD_MEMO), // Returns null if field doesn't exist or is null
            amount = doc.getDouble(FIELD_AMOUNT),
            confirmations = doc.getInteger(FIELD_CONFIRMATIONS),
            detectedAtMicros = doc.getLong(FIELD_DETECTED_AT)
        )
    }
}
