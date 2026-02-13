# ADR 0001: Ad Visit Analytics Implementation

## Status
Accepted

## Context
Gimlee needs to track unique daily/monthly/yearly ad visits to incentivize users to use the platform and post higher-quality ads.
The system must:
1. Prevent duplicate counting from the same visitor.
2. Filter out bot traffic.
3. Keep storage compact.
4. Scale efficiently even for very popular ads with millions of visits.

## Decision
We will implement an analytics system using a combination of infrastructure-level fingerprinting/scoring and application-level deduplicated storage.

### Infrastructure (Traefik + CrowdSec)
- **CrowdSec**: Used to score traffic and identify bots. It will provide an `X-Crowdsec-Bot-Score` header.
- **Traefik**: Used for client fingerprinting. It will generate an `X-Client-Id` header based on IP and User-Agent hash.

### Application (gimlee-ads)
- **Deduplication**: We will use `xxHash64` to generate a 64-bit hash of `clientId + adId + date`.
- **Storage**: MongoDB will store daily visit counts in a compact format.
- **Performance Optimization**: To handle high-traffic ads without hitting MongoDB document size limits (16MB) or suffering from slow `$addToSet` operations on large arrays, we use a two-step process:
    1. **Deduplication Collection (`gimlee-ads-ad-visit-dedup`)**: Stores a small document for each unique visit of the day. The `_id` is a composite string: `adId|dateInt|hash`. A TTL index automatically expires these documents after 24 hours.
    2. **Stats Collection (`gimlee-ads-ad-visits`)**: Stores aggregated daily counts. We use `updateOne` with `$inc` to increment the count only if the insertion into the deduplication collection succeeded.
- **Data Model**:
    - `aid`: Stored as `ObjectId` for efficient indexing and relationship with the ads collection.
    - `d`: Date as `YYYYMMDD` integer for compact range queries.
    - `c`: Integer count of unique visits.
    - `exp`: Date field used for the 365-day TTL index.

### API (gimlee-api)
- `GET /api/ads/{adId}/stats`: Returns daily, monthly, yearly, and total visit counts.
- Recording visits is handled automatically via the `@Analytics` aspect on the `fetchAd` controller method.

## Consequences
- **Positive**: High performance and scalability. No limit on the number of unique visits per ad. Bot filtering at the edge.
- **Negative**: Two database operations per unique visit (one for dedup, one for count increment).
