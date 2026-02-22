package com.gimlee.payments.persistence

import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.payments.domain.model.ExchangeRate
import com.gimlee.payments.persistence.model.ExchangeRateDocument
import com.gimlee.payments.persistence.model.ExchangeRateDocument.Companion.FIELD_BASE_CURRENCY
import com.gimlee.payments.persistence.model.ExchangeRateDocument.Companion.FIELD_ID
import com.gimlee.payments.persistence.model.ExchangeRateDocument.Companion.FIELD_IS_VOLATILE
import com.gimlee.payments.persistence.model.ExchangeRateDocument.Companion.FIELD_QUOTE_CURRENCY
import com.gimlee.payments.persistence.model.ExchangeRateDocument.Companion.FIELD_RATE
import com.gimlee.payments.persistence.model.ExchangeRateDocument.Companion.FIELD_SOURCE
import com.gimlee.payments.persistence.model.ExchangeRateDocument.Companion.FIELD_UPDATED_AT
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import org.bson.Document
import org.bson.types.Decimal128
import org.springframework.stereotype.Repository

@Repository
class ExchangeRateRepository(
    private val mongoDatabase: MongoDatabase
) {
    companion object {
        const val COLLECTION_NAME = "gimlee-payments-exchange-rates"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(exchangeRate: ExchangeRate) {
        val doc = exchangeRate.toDocument()
        val bson = mapToBsonDocument(doc)
        collection.insertOne(bson)
    }

    fun findLatest(baseCurrency: Currency, quoteCurrency: Currency): ExchangeRate? {
        val filter = Filters.and(
            Filters.eq(FIELD_BASE_CURRENCY, baseCurrency.name),
            Filters.eq(FIELD_QUOTE_CURRENCY, quoteCurrency.name)
        )
        return collection.find(filter)
            .sort(Sorts.descending(FIELD_UPDATED_AT))
            .firstOrNull()
            ?.toExchangeRate()
    }

    fun findAllLatest(): List<ExchangeRate> {
        val pipeline = listOf(
            Aggregates.sort(Sorts.descending(FIELD_UPDATED_AT)),
            Aggregates.group(
                Document(FIELD_BASE_CURRENCY, "\$$FIELD_BASE_CURRENCY")
                    .append(FIELD_QUOTE_CURRENCY, "\$$FIELD_QUOTE_CURRENCY"),
                Accumulators.first("doc", "\$\$ROOT")
            ),
            Aggregates.replaceRoot("\$doc")
        )
        return collection.aggregate(pipeline).map { it.toExchangeRate() }.toList()
    }

    fun clear() {
        collection.deleteMany(Document())
    }

    fun count(): Long {
        return collection.countDocuments()
    }

    fun findRatesInWindow(baseCurrency: Currency, quoteCurrency: Currency, fromMicros: Long, toMicros: Long): List<ExchangeRate> {
        val filter = Filters.and(
            Filters.eq(FIELD_BASE_CURRENCY, baseCurrency.name),
            Filters.eq(FIELD_QUOTE_CURRENCY, quoteCurrency.name),
            Filters.gte(FIELD_UPDATED_AT, fromMicros),
            Filters.lte(FIELD_UPDATED_AT, toMicros)
        )
        return collection.find(filter)
            .sort(Sorts.descending(FIELD_UPDATED_AT))
            .map { it.toExchangeRate() }
            .toList()
    }

    fun deleteOlderThan(timestampMicros: Long): Long {
        val filter = Filters.lt(FIELD_UPDATED_AT, timestampMicros)
        return collection.deleteMany(filter).deletedCount
    }

    private fun ExchangeRate.toDocument(): ExchangeRateDocument =
        ExchangeRateDocument(
            baseCurrency = baseCurrency.name,
            quoteCurrency = quoteCurrency.name,
            rate = Decimal128(rate),
            updatedAtMicros = updatedAt.toMicros(),
            source = source,
            isVolatile = isVolatile
        )

    private fun mapToBsonDocument(doc: ExchangeRateDocument): Document {
        return Document()
            .append(FIELD_ID, doc.id)
            .append(FIELD_BASE_CURRENCY, doc.baseCurrency)
            .append(FIELD_QUOTE_CURRENCY, doc.quoteCurrency)
            .append(FIELD_RATE, doc.rate)
            .append(FIELD_UPDATED_AT, doc.updatedAtMicros)
            .append(FIELD_SOURCE, doc.source)
            .append(FIELD_IS_VOLATILE, doc.isVolatile)
    }

    private fun Document.toExchangeRate(): ExchangeRate = ExchangeRate(
        baseCurrency = Currency.valueOf(getString(FIELD_BASE_CURRENCY)),
        quoteCurrency = Currency.valueOf(getString(FIELD_QUOTE_CURRENCY)),
        rate = get(FIELD_RATE, Decimal128::class.java).bigDecimalValue(),
        updatedAt = fromMicros(getLong(FIELD_UPDATED_AT)),
        source = getString(FIELD_SOURCE),
        isVolatile = getBoolean(FIELD_IS_VOLATILE) ?: false
    )
}
