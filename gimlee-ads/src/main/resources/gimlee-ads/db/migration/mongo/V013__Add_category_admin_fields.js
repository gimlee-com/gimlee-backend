// Adds admin management fields to the gimlee-ads-categories collection:
// - ord (displayOrder): sequential order within each parent group, alphabetical by en-US name
// - f.hdn (hidden): default false
// - f.ao (adminOverride): default false
// - Index on {p: 1, ord: 1} for efficient sibling ordering queries

var collection = db.getCollection("gimlee-ads-categories");

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
var parents = collection.distinct("p");
parents.push(null);

parents.forEach(function(parentId) {
    var filter = parentId === null ? { p: null } : { p: parentId };
    var siblings = collection.find(filter).sort({ "n.en-US.name": 1 }).toArray();
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
