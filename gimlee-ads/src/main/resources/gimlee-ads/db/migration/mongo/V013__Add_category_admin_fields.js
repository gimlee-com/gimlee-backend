// Adds admin management fields to the gimlee-ads-categories collection:
// - ord (displayOrder): sequential order within each parent group, alphabetical by en-US name
// - f.hdn (hidden): default false
// - f.ao (adminOverride): default false
// - Index on {p: 1, ord: 1} for efficient sibling ordering queries

const collectionName = "gimlee-ads-categories";

if (!db.getCollectionNames().includes(collectionName)) {
    print("Collection " + collectionName + " does not exist. Skipping migration.");
} else {
    const collection = db.getCollection(collectionName);

    // Set default flags for all existing categories
    collection.updateMany(
        { "f.hdn": { $exists: false } },
        { $set: { "f.hdn": false } }
    );

    collection.updateMany(
        { "f.ao": { $exists: false } },
        { $set: { "f.ao": false } }
    );

    // Assign sequential displayOrder within each parent group, alphabetical by en-US name
    const parents = collection.distinct("p");
    // Include null parent (root categories)
    parents.push(null);

    parents.forEach(function(parentId) {
        const filter = parentId === null ? { p: null } : { p: parentId };
        const siblings = collection.find(filter).sort({ "n.en-US.name": 1 }).toArray();
        siblings.forEach(function(sibling, index) {
            collection.updateOne(
                { _id: sibling._id },
                { $set: { ord: index } }
            );
        });
    });

    // Create index for efficient sibling ordering queries
    collection.createIndex(
        { "p": 1, "ord": 1 },
        { name: "idx_parent_display_order" }
    );

    print("Migration V013 completed: admin fields added to " + collectionName);
}
