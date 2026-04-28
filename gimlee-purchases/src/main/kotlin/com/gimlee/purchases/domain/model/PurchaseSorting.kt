package com.gimlee.purchases.domain.model

enum class PurchaseSortField(val mongoField: String) {
    DATE("ca"),
    AMOUNT("tamt")
}

enum class PurchaseSortDirection {
    ASC, DESC
}

data class PurchaseSorting(
    val by: PurchaseSortField = PurchaseSortField.DATE,
    val direction: PurchaseSortDirection = PurchaseSortDirection.DESC
)
