## mongodb collection design guidelines:
1. Indexes/ensuring indexes should not be a part of code - they are managed via flyway migrations. See
   the README section on [MongoDB indexes](../../README.md#mongodb-indexes) for details.
2. It is forbidden to use any annotations on mongo model classes (they should be plain kotlin data classes)
3. The use of any Spring data mongodb abstractions is forbidden
4. All the timestamps should be epoch microseconds (use [Instant.toMicros](../../gimlee-common/src/main/kotlin/com/gimlee/common/InstantUtils.kt)
   extension function to convert to microseconds)
5. All the mongo field names should be abbreviations for minimal data size impact
6. **Index Optimization:**
    *   **Hit-only indexing:** Only create indexes for fields or combinations of fields that are actually hit by repository query methods.
    *   **Partial Indexes:** For fields with low cardinality (like `status`), do NOT create a full index. Use **partial indexes** with a `partialFilterExpression` for specific values that are frequently queried (e.g., `status: "ACTIVE"` or `status: "AWAITING_PAYMENT"`).
    *   **Compound Prefix Rule:** When creating compound indexes, ensure they are designed such that they can also serve queries for their prefix fields, avoiding redundant single-field indexes.