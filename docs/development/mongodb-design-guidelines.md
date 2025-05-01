## mongodb collection design guidelines:
1. Indexes/ensuring indexes should not be a part of code (indexes should be created separately, by hand).
2. It is forbidden to use any annotations on mongo model classes (they should be plain kotlin data classes)
3. The use of any Spring data mongodb abstractions is forbidden
4. All the timestamps should be epoch microseconds (use [Instant.toMicros](../../gimlee-common/src/main/kotlin/com/gimlee/common/InstantUtils.kt)
   extension function to convert to microseconds)
5. All the mongo field names should be abbreviations for minimal data size impact