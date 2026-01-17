// Flyway migration script
// Version: 4
// Description: Migrate ads to store category path and create index

const adsCollectionName = "gimlee-ads-advertisements";
const categoriesCollectionName = "gimlee-ads-categories";
const adsCollection = db.getCollection(adsCollectionName);
const categoriesCollection = db.getCollection(categoriesCollectionName);

// 1. Build a map of category parents for resolution
const categoryParents = {};
categoriesCollection.find({}, { _id: 1, p: 1 }).forEach(cat => {
    // Both _id and p are UUIDs (BinData)
    categoryParents[cat._id.toString()] = cat.p;
});

function resolvePath(leafId) {
    const path = [];
    let currentId = leafId;
    while (currentId) {
        path.unshift(currentId);
        const parentId = categoryParents[currentId.toString()];
        currentId = parentId;
    }
    return path;
}

// 2. Migrate existing ads
adsCollection.find({ catid: { $exists: true } }).forEach(ad => {
    const leafId = ad.catid;
    const path = resolvePath(leafId);
    adsCollection.updateOne(
        { _id: ad._id },
        {
            $set: { cats: path },
            $unset: { catid: "" }
        }
    );
});

// 3. Create new index for category filtering and sorting by creation date
adsCollection.createIndex({ "cats": 1, "crt": -1 });
