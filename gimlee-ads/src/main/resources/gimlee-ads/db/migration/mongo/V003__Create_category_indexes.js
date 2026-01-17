// Flyway migration script
// Version: 003
// Description: Create indexes for the gimlee-ads-categories collection

const collectionName = "gimlee-ads-categories";
const collection = db.getCollection(collectionName);

// 1. Index on source.id (with partial filter for GPT)
// Requirements: "The collection... should have an index on... the source id field (but with partial filter expression matching GPT)"
collection.createIndex(
    { "source.id": 1 },
    {
        name: "idx_source_gpt_id",
        partialFilterExpression: { "source.type": "GPT" }
    }
);

// 2. Index on name slugs (all languages)
// Requirements: "an index on all the language/slug pairs"
// We use a wildcard index on the 'name' field to cover all language keys (e.g. name.en-US, name.pl-PL)
// This will index all subfields, including 'slug' and 'name'.
// Query pattern: { "name.en-US.slug": "slug-value" }
collection.createIndex(
    { "name.$**": 1 },
    { name: "idx_name_wildcard" }
);

