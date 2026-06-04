# ADR 0004: User Ratings & Reviews

## Status
Proposed

## Context
Gimlee is a trust-critical, non-custodial P2P marketplace. Because the platform never holds funds and buyers/sellers transact directly, **reputation is the primary trust signal**. Today there is no way for a user to rate a counterparty after a transaction, and the front-end ("User Spaces" — public profile pages that "showcase ads, build reputation, and establish a unique identity") already assumes a reputation system will exist.

We want a ratings & reviews subsystem with the following properties:

1. **Transaction-anchored**: every completed purchase enables the participants to rate each other (1–5 stars, free-text review, optional photos), scoped to that specific transaction.
2. **Item snapshot linkage**: each rating links an immutable snapshot of the transacted item(s) so that, during appeals/moderation, it is clear *what* the reviewer was happy/unhappy about — even if the ad is later edited or deleted.
3. **Extensible beyond purchases**: the same mechanism must support future "rating contexts" (e.g., community help, Q&A answers, dispute behaviour) without schema migration of the core rating.
4. **Moderatable**: like all user-generated content, ratings must be reportable, soft-deletable, and visible to admins — integrating with the existing `gimlee-support` moderation pipeline.
5. **Query-ready indexes**: optimized for "latest ratings *received* by a user" and "latest ratings aggregated/written *by* a review author".
6. **Context-aware projections**: public surfaces (e.g., a seller's profile) should expose only a lightweight item reference (e.g., purchased item *name*), never the full purchase/delivery data; admins/owners get the full snapshot.
7. **Edit-then-freeze-then-supplement lifecycle**: a freshly created/edited review is freely editable for a short window, then frozen; after a cooldown the author may *append* a bounded number of supplements (but not rewrite history).
8. **Anti-impulse dwell time**: a minimum waiting period after transaction completion before a rating may be submitted, to reduce heat-of-the-moment reviews.
9. **Server-side sanitization validation**: markdown bodies/supplements/responses are sanitized client-side, but the back-end must independently validate that the submitted markdown is properly sanitized (defense-in-depth) and reject content that is not.

### Existing building blocks (surveyed)
- `gimlee-purchases`: `Purchase(buyerId, sellerId, items[adId, quantity, unitPrice, currency], status)` with terminal status `COMPLETE`.
- `gimlee-events`: `PurchaseEvent(purchaseId, buyerId, sellerId, items[adId, quantity], status, ...)` already published on status changes — the natural eligibility trigger (mirrors ADR 0002's listener pattern).
- `gimlee-support`: generic moderation. `ReportTargetType` enum (`shortName` + `fromShortName`) already lists `AD/USER/MESSAGE/QUESTION/ANSWER`; `ReportTargetResolver` interface lets a module resolve a target's title/snapshot; `ReportResolution` includes `CONTENT_REMOVED/USER_WARNED/USER_BANNED/...`.
- `gimlee-media-store`: `PictureUploadService` / `MediaService` produce stable string media paths (the Ad model already stores `mediaPaths`/`mainPhotoPath`) — reuse for review photos.
- Conventions (MongoDB design + performance guidelines): plain data classes, abbreviated `FIELD_*` constants, epoch-micros `Long` timestamps, short-name persisted enums, partial indexes, UUIDv7 where applicable, `Outcome` + `StatusResponseDto`.

## Decision

Introduce a new, generic **`gimlee-ratings`** module that owns the rating domain, and keep all Gimlee-specific orchestration (purchase eligibility, item snapshots, public projections) in `gimlee-api` + small resolvers — exactly the decoupled-orchestration shape used for chat in ADR 0002.

### Module placement & dependencies

```
gimlee-ratings → gimlee-common, gimlee-auth, gimlee-media-store   (generic, near-extractable)
gimlee-api     → gimlee-ratings, gimlee-purchases, gimlee-ads, gimlee-support, gimlee-events  (orchestration)
gimlee-support → (no new compile dep on ratings) — resolves RATING targets via a ReportTargetResolver bean contributed by gimlee-api/ratings
```

**Why a dedicated module (not inside `gimlee-purchases` or `gimlee-user`)?** Requirement #3 demands the rating mechanism outlive its purchase origin. Coupling it to purchases would force the community/Q&A use cases to depend on the purchases module (SRP violation + circular-dependency risk). `gimlee-ratings` is purchase-agnostic; purchases is just the *first consumer*.

> Settlement note: `settings.gradle.kts`, the root `Dockerfile` (`COPY` for the new module), and `spring.messages.basename` must all be updated when the module is added (per Docker/i18n guidelines).

### Core concept: the "Rating Context"

A rating is never free-floating; it is always anchored to a **context** that earns one party the right to rate another. The context is modelled the same opaque way `gimlee-chat` models conversation `type`/`linkType`:

- `contextType: String` — opaque slug owned by consumers. First value: `"ORDER"`. Future: `"COMMUNITY_HELP"`, `"QA_ANSWER"`, `"DISPUTE"`.
- `contextId: String` — the id of the originating entity (e.g., the `purchaseId`).
- `subjectKind: String` — what is being rated. v1: `"USER"` (user-to-user). Designed so a future `"AD"`/`"SELLER_PROFILE"` subject needs no schema change.

The `gimlee-ratings` module **never branches on `contextType`**. Semantics (who may rate whom, what window, what snapshot) live in pluggable strategies registered by consumers.

### Eligibility: who can rate, and when

Rather than letting clients POST arbitrary ratings, eligibility is **minted** by the context owner and stored as a short, queryable record. This prevents fabricated reviews (a fundamental trust requirement) and makes "you have N pending reviews" trivially queryable for the UI.

Flow for purchases (mirrors ADR 0002 listeners):

```
PurchaseService → publishes PurchaseEvent(status=COMPLETE)        [gimlee-purchases / gimlee-events]
  → PurchaseRatingEligibilityListener  [gimlee-api]
      @EventListener on PurchaseEvent where status == COMPLETE
      → builds item snapshot (resolves ad titles/main photo at completion time)
      → RatingEligibilityService.grant(
            contextType="ORDER", contextId=purchaseId,
            raterId=buyerId,  rateeId=sellerId, snapshot=...   // buyer rates seller
            raterId=sellerId, rateeId=buyerId,  snapshot=...   // seller rates buyer (reciprocal)
        )
```

Each grant is one `RatingEligibility` document. Submitting a rating "consumes" the matching eligibility (atomic conditional update — see Atomic Operations guideline). This also yields the **"Verified purchase"** badge the UI can display, and a configurable **rating window** (e.g., 60 days after completion) after which eligibility expires.

> A `RatingContextStrategy` interface (Spring `@Order`, same pattern as `ConversationPolicy`) lets each `contextType` define: allowed rater/ratee roles, whether ratings are reciprocal, the dwell time, the rating window, the edit/supplement policy, and which **reputation kind** the rating contributes to. A `DefaultRatingContextStrategy` (`@Order(Int.MAX_VALUE)`) provides safe defaults.

#### Dwell time (anti-impulse, Requirement #8)

A rating may not be submitted immediately after completion. Each eligibility carries an `activeFrom` timestamp = `completedAt + dwell` (configurable, **initial value: 7 days**). Before `activeFrom`, the eligibility exists (so the UI can show "you'll be able to review this on <date>") but `POST /ratings` against it is rejected with `RATING_DWELL_NOT_ELAPSED`. The dwell is defined per `contextType` via the strategy, so future contexts (e.g., community help) can use a different — or zero — dwell.

#### Eligibility expiry (Requirement #6 — hard delete)

When a `PENDING` eligibility passes `expiresAt` without being consumed, it is **hard-deleted** by the sweeper (and/or a MongoDB TTL index). We do *not* retain `EXPIRED` tombstones — an unconsumed right-to-rate has no analytical value worth the storage, and the `(ct,cid,rtr)` uniqueness is naturally freed for any legitimate re-grant. `CONSUMED` eligibilities are retained (they carry the produced `ratingId` and the "verified purchase" provenance).

### Edit / Freeze / Supplement lifecycle (Requirement #7)

To balance "let people fix mistakes" against "reviews are a trust record, not a moving target", a rating moves through three editing phases. The whole policy is driven by the `RatingContextStrategy` and configurable durations:

1. **Free-edit window** — On **create**, and again on **every subsequent edit**, `editableUntil = now + edit-window-minutes` (configurable; initial candidates **10 or 30 minutes**). While `now < editableUntil`, the author may freely and repeatedly change `score`, `title`, `body`, and `photos` (an unlimited number of edits — each one *slides* the window forward by re-stamping `editableUntil`). The first such change after the very first publish sets `edited = true`. This window is the "I forgot a sentence / fixed a typo / changed my mind" affordance.

2. **Frozen** — Once `now >= editableUntil`, the rating body, score, title, and photos are **immutable**. `PATCH /ratings/{id}` is rejected with `RATING_EDIT_WINDOW_CLOSED`. This snapshots the user's settled opinion and prevents silent score-flipping after the counterparty has acted on it.

3. **Supplement (post-cooldown append-only)** — After a **cooldown** measured from the last edit/freeze (configurable, **e.g., 1 week**), the author may **append a supplement**: an additional, timestamped markdown note that is *added to* the review, never overwriting the original body or score. Supplements are capped at **`max-supplements` (e.g., 4)**. This supports "an update after a week of use" / "follow-up on how the dispute was resolved" without enabling history rewriting. Each supplement append is itself subject to its own short free-edit window (so a typo in a supplement can be fixed immediately, then it too freezes).

Rules enforced server-side:
- A supplement is only allowed when `now >= max(editableUntil, lastSupplementAt) + supplement-cooldown-days` and `supplements.size < max-supplements`, else `RATING_SUPPLEMENT_TOO_SOON` / `RATING_SUPPLEMENT_LIMIT_REACHED`.
- The original `score` is fixed forever once frozen — supplements are text-only and do **not** alter the aggregate score.
- Supplements are full UGC: each is server-sanitization-validated, reportable, and individually soft-deletable by moderation (the `sup[]` entry carries its own minimal state if hidden).

### Server-side sanitization validation (Requirement #9)

The front-end renders bodies via its sanitizing `Markdown` component and is expected to submit already-sanitized markdown. The back-end **must not trust this**. On `POST /ratings`, `PATCH /ratings/{id}`, supplement append, and ratee `response`, the server runs the markdown/HTML through a server-side sanitizer (e.g., an allow-list HTML sanitizer such as OWASP Java HTML Sanitizer / jsoup safelist) and:
- If the sanitized output differs materially from the input (i.e., the client sent disallowed/unsafe markup), reject with `RATING_BODY_NOT_SANITIZED` rather than silently storing the cleaned version — this surfaces client bugs/tampering instead of masking them.
- Enforce `max-body-length` and a markdown feature allow-list (no raw HTML, no scripts, no inline event handlers, restricted link schemes).
- This is defense-in-depth: it protects every future non-browser client and guards against a compromised/bypassed front-end, consistent with the "Informative but Safe" Outcome guideline.

### Data Model

Three collections. All timestamps are epoch micros (`Long`); enums persisted by `shortName`; ids are UUIDv7 strings unless an existing `ObjectId` is referenced.

#### 1. `gimlee-ratings-ratings` — the rating itself (one per rater→ratee per context)

| Domain Field   | DB Field | Type     | Description                                                                 |
|----------------|----------|----------|-----------------------------------------------------------------------------|
| id             | _id      | String   | UUIDv7                                                                       |
| contextType    | ct       | String   | Opaque context slug ("ORDER", ...)                                          |
| contextId      | cid      | String   | Originating entity id (e.g., purchaseId)                                     |
| subjectKind    | sk       | String   | What is rated ("USER" in v1)                                                |
| repKind        | rk       | String   | Reputation kind fed by this rating (SEL = seller / BUY = buyer)             |
| raterId        | rtr      | String   | Author of the review                                                        |
| rateeId        | rte      | String   | User receiving the rating                                                   |
| score          | sc       | Int      | 1–5 stars                                                                    |
| title          | ttl      | String?  | Optional short headline                                                     |
| body           | bd       | String?  | Free-text review (markdown, sanitized client-side **and** server-validated) |
| photoPaths     | ph       | Array?   | Media-store paths (reuse Ad media pipeline)                                  |
| snapshot       | snp      | Object?  | Embedded `RatingSubjectSnapshot` (see below)                                |
| status         | st       | String   | RatingStatus short name (PUB / HID / DEL)                                    |
| edited         | ed       | Boolean  | True if edited at least once after first publish                            |
| editableUntil  | eu       | Long     | Epoch micros — free-edit window end; reset on every edit (see lifecycle)    |
| supplements    | sup      | Array?   | Append-only addenda `[{ bd, ca }]` added after freeze + cooldown (bounded)  |
| response       | rsp      | Object?  | Optional ratee reply: `{ body, createdAt, updatedAt }`                       |
| helpfulCount   | hc       | Int      | Cached count of "helpful" votes (future-facing)                             |
| reportCount    | rc       | Int      | Cached count of moderation reports (for admin triage)                       |
| createdAt      | ca       | Long     | Epoch micros                                                                |
| updatedAt      | ua       | Long     | Epoch micros                                                                |
| publishedAt    | pa       | Long?    | Epoch micros — used for "latest" sorting (null while DEL/HID)               |

#### 2. `gimlee-ratings-eligibility` — minted right-to-rate

| Domain Field | DB Field | Type    | Description                                                  |
|--------------|----------|---------|--------------------------------------------------------------|
| id           | _id      | String  | UUIDv7                                                       |
| contextType  | ct       | String  | "ORDER", ...                                                |
| contextId    | cid      | String  | purchaseId, ...                                             |
| raterId      | rtr      | String  | Who may rate                                                |
| rateeId      | rte      | String  | Who will be rated                                          |
| repKind      | rk       | String  | Reputation kind this rating feeds (SEL / BUY) — see aggregates |
| snapshot     | snp      | Object  | Pre-built `RatingSubjectSnapshot` carried onto the rating   |
| status       | st       | String  | PENDING / CONSUMED  (no EXPIRED — expired grants are hard-deleted) |
| ratingId     | rid      | String? | Set when consumed → the produced rating id                  |
| activeFrom   | af       | Long    | Epoch micros — dwell end; rating allowed only when now ≥ af |
| expiresAt    | exp      | Long    | Epoch micros — end of rating window (hard-deleted past this)|
| createdAt    | ca       | Long    | Epoch micros                                                |

#### 3. `gimlee-ratings-aggregates` — denormalized reputation per ratee **per reputation kind**

Kept eventually-consistent via atomic `$inc`/recompute on each rating publish/hide/delete, so profile pages never run an aggregation on the hot path.

Per the team decision, reputation is **split by role**: a user's *seller* reputation and *buyer* reputation are tracked **separately** so a great seller who is a flaky buyer (or vice-versa) is represented honestly, and the UI can show the relevant score in context (e.g., seller score on a listing, buyer score in a sale). The aggregate is therefore keyed by `(rateeId, repKind)`.

| Domain Field   | DB Field | Type   | Description                                                       |
|----------------|----------|--------|-------------------------------------------------------------------|
| id             | _id      | Object | Composite natural key `{ rte, rk }` (rateeId + reputation kind)   |
| rateeId        | rte      | String | The rated user id                                                 |
| repKind        | rk       | String | Reputation kind: SEL (seller) / BUY (buyer)                       |
| subjectKind    | sk       | String | "USER"                                                            |
| count          | n        | Int    | Number of visible ratings of this kind                            |
| sum            | sm       | Long   | Sum of scores (avg = sm / n)                                      |
| dist           | ds       | Object | Star histogram `{ "1":x, ..., "5":y }`                            |
| lastRatingAt   | lr       | Long?  | Epoch micros of latest visible rating                             |
| updatedAt      | ua       | Long   | Epoch micros                                                      |

> `repKind` is decided by the `RatingContextStrategy` when eligibility is minted, not by the client. For the `ORDER` context: *buyer-rates-seller* feeds the seller's `SEL` aggregate; *seller-rates-buyer* feeds the buyer's `BUY` aggregate. New contexts can introduce additional kinds (e.g., `HELPER`) without touching the core.

#### Embedded: `RatingSubjectSnapshot`

The immutable record of "what this was about" (Requirement #2). Built once, at eligibility-grant time, and **never recomputed** — so editing/deleting the ad does not rewrite history.

| Domain Field | DB Field | Type   | Description                                                        |
|--------------|----------|--------|--------------------------------------------------------------------|
| refType      | rt       | String | Snapshot kind ("ORDER_ITEMS")                                      |
| items        | it       | Array  | List of snapshotted items                                         |
| ∟ adId       | aid      | String | Original ad id (may now be deleted/edited)                        |
| ∟ name       | nm       | String | **Ad title at transaction time** — the only field shown publicly  |
| ∟ quantity   | qty      | Int    | Quantity purchased                                                |
| ∟ unitPrice  | up       | String | Decimal128/string price (respects `Currency.decimalPlaces`)       |
| ∟ currency   | cur      | String | Settlement currency code                                         |
| ∟ thumbPath  | tp       | String?| Media path of the item's main photo at snapshot time              |

> The snapshot is intentionally **self-contained** — it holds the data needed for moderation/appeals without a live join to `gimlee-ads` or `gimlee-purchases` (decoupling + history integrity). Public projections expose only `nm` (and optionally `thumbPath`); price/quantity/purchase linkage are owner/admin-only.

#### Embedded: `RatingSupplement` (element of `sup[]`)

| Domain Field | DB Field | Type   | Description                                                  |
|--------------|----------|--------|--------------------------------------------------------------|
| id           | sid      | String | UUIDv7 — addressable for moderation/reporting of a supplement |
| body         | bd       | String | Markdown note (server-sanitization-validated)                |
| status       | st       | String | PUB / HID / DEL — individually moderatable                   |
| editableUntil| eu       | Long   | Epoch micros — short free-edit window for this supplement    |
| createdAt    | ca       | Long   | Epoch micros                                                 |

### Indexes (Flyway Migration V001 — `gimlee-ratings`)

> **Index design principle — no low-cardinality keys.** Enum-like, low-cardinality fields (`st` status, `rk` reputation kind) are **never** used as index *keys* (neither leading nor mid-key). They are expressed as **partial filter expressions** instead — matching the project's "partial indexes for low-cardinality statuses" guideline. Where a low-cardinality field is part of a **uniqueness** tuple and therefore can be neither dropped nor partial-filtered (`ct` in the two `*_unique` indexes), it is pushed to the **last** position behind a high-cardinality prefix. Every index here is led by a high-cardinality field (`rte`, `rtr`, `cid`, `exp`).

```javascript
// Ratings: latest PUBLISHED ratings RECEIVED by a user, SELLER reputation (public profile).
// Low-card `rk`/`st` are expressed as a partial filter, not as index keys.
db.getCollection("gimlee-ratings-ratings").createIndex(
  { rte: 1, pa: -1 },
  { name: "idx_ratee_publishedAt_seller", partialFilterExpression: { rk: "SEL", st: "PUB" } }
);

// Ratings: latest PUBLISHED ratings RECEIVED by a user, BUYER reputation (public profile).
db.getCollection("gimlee-ratings-ratings").createIndex(
  { rte: 1, pa: -1 },
  { name: "idx_ratee_publishedAt_buyer", partialFilterExpression: { rk: "BUY", st: "PUB" } }
);

// Ratings: latest ratings WRITTEN BY an author (aggregated-by-author view)
db.getCollection("gimlee-ratings-ratings").createIndex(
  { rtr: 1, pa: -1 },
  { name: "idx_rater_publishedAt" }
);

// One rating per (rater, ratee, context) — idempotency / anti-duplicate.
// `ct` is low-cardinality and part of the uniqueness tuple, so it can't be dropped or
// partial-filtered → it is placed LAST; high-cardinality `cid` (e.g., purchaseId) leads.
db.getCollection("gimlee-ratings-ratings").createIndex(
  { cid: 1, rtr: 1, rte: 1, ct: 1 },
  { unique: true, name: "idx_context_rater_ratee_unique" }
);

// Admin/moderation triage: most-reported first (partial — only rows that have reports)
db.getCollection("gimlee-ratings-ratings").createIndex(
  { rc: -1, ca: -1 },
  { name: "idx_reportCount_createdAt", partialFilterExpression: { rc: { $gt: 0 } } }
);

// Eligibility: a rater's actionable + upcoming reviews ("you have N to write", dwell countdown).
// Low-card `st` lifted out of the key into a partial filter — only PENDING is ever listed here.
db.getCollection("gimlee-ratings-eligibility").createIndex(
  { rtr: 1, af: 1 },
  { name: "idx_rater_activeFrom_pending", partialFilterExpression: { st: "PND" } }
);

// Eligibility: idempotent grant per (context, rater) — unique.
// `ct` (low-card) kept LAST behind high-cardinality `cid`, same rationale as the ratings uniqueness index.
db.getCollection("gimlee-ratings-eligibility").createIndex(
  { cid: 1, rtr: 1, ct: 1 },
  { unique: true, name: "idx_context_rater_unique" }
);

// Eligibility: sweeper support — find PENDING grants past the window to HARD-DELETE.
// Low-card `st` replaced by a partial filter; the key is just the high-cardinality `exp`.
db.getCollection("gimlee-ratings-eligibility").createIndex(
  { exp: 1 },
  { name: "idx_pending_expiry", partialFilterExpression: { st: "PND" } }
);

// Aggregates: per-user, per reputation-kind lookup (seller vs buyer score) — unique.
// `rk` is low-card but part of the uniqueness tuple; it is already LAST, behind high-card `rte`.
db.getCollection("gimlee-ratings-aggregates").createIndex(
  { rte: 1, rk: 1 },
  { unique: true, name: "idx_ratee_repKind_unique" }
);
```

> The two per-repKind "latest received" partial indexes (`{rte,pa}` filtered on `rk`+`st=PUB`) and the "latest by author" (`{rtr,pa}`) index directly satisfy Requirement #5: each is led by a high-cardinality field, the partial `PUB` filter keeps soft-deleted/hidden rows out of public listings entirely (so they never even enter the index), and the seller/buyer split lets a profile fetch seller-only or buyer-only reviews from a single selective index. A plain Mongo **TTL index is deliberately not used** on eligibility `exp`, because `CONSUMED` records (which retain the produced `ratingId`/verified-purchase provenance) must survive — instead the frequent small-batch sweeper hard-deletes only `PENDING` grants past `exp` (Requirement #6).

#### Enums

- `RatingStatus`: `PUBLISHED("PUB")`, `HIDDEN("HID")` (temporarily withheld by moderation/author), `DELETED("DEL")` (soft-deleted). Only `PUB` contributes to aggregates and public listings.
- `EligibilityStatus`: `PENDING("PND")`, `CONSUMED("CON")`. There is **no** `EXPIRED` state — expired `PENDING` grants are hard-deleted by the sweeper (Requirement #6).
- `ReputationKind`: `SELLER("SEL")`, `BUYER("BUY")` — extensible (e.g., future `HELPER`).
- All follow the project enum convention: descriptive name + `shortName` + `fromShortName` companion lookup.

### Moderation Integration (`gimlee-support`)

Ratings plug into the existing report pipeline with **no new coupling in `gimlee-support`**:

1. Add `RATING("R")` to `ReportTargetType`.
2. Provide a `RatingReportTargetResolver : ReportTargetResolver` (in `gimlee-ratings`, contributed as a Spring bean) that `supports(RATING)` and `resolve(RATING, ratingId)` returns a `ReportTargetInfo` with `targetTitle = "<score>★ review of <rateeName>"` and a `targetSnapshot` containing the body/photos/`RatingSubjectSnapshot` so moderators see the full picture.
3. **Soft-delete** = set rating `status = DEL`, null `publishedAt`, and decrement the ratee's aggregate atomically. This is reversible (restore → `PUB`). Hard deletion is never used (audit/appeal trail).
4. Moderator actions reuse `ReportResolution` (`CONTENT_REMOVED` → hide/soft-delete the rating; `USER_WARNED`/`USER_BANNED` apply to the rater). When a report is filed against a rating, `reportCount`/`rc` is `$inc`-ed for admin triage (the `idx_reportCount_createdAt` index).
5. **Self-service**: an author may delete their own rating (→ `DEL`); a ratee may post one **public response** (`rsp`) but cannot delete or edit the rating itself.
6. **Supplement moderation**: individual `sup[]` entries are independently addressable (`sid`) and moderatable (`PUB/HID/DEL`), so an abusive supplement can be removed without taking down the underlying review.

### Context-aware Projections (Requirement #6)

The same rating is exposed through three DTO projections, chosen by the controller/enrichment layer — analogous to the configurable `AdEnrichmentService` pattern:

| Projection            | Audience                         | Item info exposed                  | Includes                                              |
|-----------------------|----------------------------------|------------------------------------|-------------------------------------------------------|
| `PublicRatingDto`     | Anyone viewing a profile/Space   | item **name** only (`snp.it[].nm`) | score, title, body, supplements, photos, raterHandle (User-Space), publishedAt, edited, response |
| `OwnerRatingDto`      | The rater or ratee               | name + thumb + qty/price           | + contextType, "verified purchase" flag, edit affordances |
| `AdminRatingDto`      | Support/moderation               | full `RatingSubjectSnapshot`       | + status, reportCount, raterId/rateeId, contextId, timeline |

The public projection never serializes `contextId`, prices, quantities, buyer/seller ids, or delivery data — preventing transaction-detail leakage on seller profiles.

**Rater identity (decided):** public reviews are attributed to the rater's **User-Space handle** (the public profile handle), linking through to their Space, with a `GeometricAvatar` fallback (front-end "Identity-First Trust"). This is the deliberate "open-source, full-transparency" default for the initial phase — no anonymous/aggregate-only reviews. A future enhancement may let a rater opt into anonymization via a checkbox at submission time (a per-rating `anonymous` flag that the public projection would honor); this is intentionally **out of scope for v1**.

### API Surface (`gimlee-api` facade + `gimlee-ratings` controllers)

`.http` files + OpenAPI annotations required per API-documentation guidelines. `Outcome`/`StatusResponseDto` for all responses.

| Method & Path                                   | Auth        | Purpose                                                        |
|-------------------------------------------------|-------------|----------------------------------------------------------------|
| `GET  /users/{userId}/ratings`                  | public      | Latest ratings received (paged, 0-indexed); `?repKind=SEL\|BUY` → `PublicRatingDto` |
| `GET  /users/{userId}/ratings/summary`          | public      | Aggregate per `repKind` (avg, count, star histogram) from `aggregates` |
| `GET  /ratings/authored`                        | self        | Latest ratings written by the caller → aggregated-by-author    |
| `GET  /ratings/pending`                         | self        | Eligibilities: actionable now + dwell countdown (`activeFrom`) |
| `POST /ratings`                                 | self        | Submit a rating (consumes a PENDING eligibility; dwell-gated, server-sanitized) |
| `PATCH /ratings/{id}`                           | author      | Edit while within the free-edit window (re-stamps `editableUntil`) |
| `POST /ratings/{id}/supplements`                | author      | Append a supplement after cooldown (bounded by `max-supplements`) |
| `DELETE /ratings/{id}`                          | author      | Soft-delete own rating                                         |
| `GET  /ratings/public/{id}`                     | public      | Fetch a single rating by ID (no auth required)                 |
| `POST /ratings/{id}/response`                   | ratee       | Post/update public response                                    |
| `POST /ratings/{id}/photos`                     | author      | Upload review photo (delegates to `gimlee-media-store`)        |
| `POST /reports` (existing)                      | self        | Report a rating (`targetType=RATING`)                          |
| `GET/POST /admin/ratings...` (via support)      | moderator   | Admin listing / hide / restore via report resolution           |

A `POST /ratings` request DTO carries minimal intent: `{ eligibilityId, score, title?, body?, photoPaths? }` (DTO-intent guideline). The server derives `contextType/contextId/rateeId/repKind/snapshot` from the consumed eligibility — clients cannot forge them. A supplement DTO is just `{ body }`; the server enforces cooldown and the `max-supplements` cap.

### Aggregation & Scheduling

- **Primary path**: aggregates updated synchronously/atomically on each publish/hide/delete (`$inc n/sm`, histogram, `lastRatingAt`).
- **Reconciliation sweeper**: a `@Scheduled` job on the shared `ThreadPoolTaskScheduler` runs on a **frequent small-batch** cadence (configurable interval + max batch size; no fixed "off-peak" time per the scheduling guideline) to (a) **hard-delete** `PENDING` eligibilities past `exp` (Requirement #6), and (b) recompute drifted per-`repKind` aggregates. Logs start (with batch size) and completion (items processed) at INFO.

### Configuration (`application.yaml` + `application-local-EXAMPLE.yaml`)

```yaml
gimlee:
  ratings:
    dwell-days: 7                 # anti-impulse: min wait after completion before rating (Ad.2)
    window-days: 60               # how long after dwell a user may still rate before eligibility expires
    edit-window-minutes: 30       # free-edit window after each create/edit; slides on every edit (Ad.1)
    supplement-cooldown-days: 7   # wait after freeze before a supplement may be appended (Ad.1)
    max-supplements: 4            # cap on append-only supplements per review (Ad.1)
    max-photos: 6
    max-body-length: 4000
    sanitization:
      reject-on-mismatch: true    # reject (not silently clean) markdown the client failed to sanitize (Req. #9)
      allowed-link-schemes: [https, mailto]
    reciprocal-by-context:
      ORDER: true                 # both buyer and seller may rate
    sweeper:
      interval-ms: 300000         # every 5 min, frequent small-batch
      max-batch-size: 10000
```

> All durations are externalized per the configuration guideline; `edit-window-minutes` initial value is 30 (10 is a viable tighter alternative), `dwell-days` initial value is 7, and `supplement-cooldown-days` / `max-supplements` default to 7 / 4.

### Outcomes & i18n

Define `RatingOutcome : Outcome` with descriptive slugs, e.g. `RATING_CREATED`, `RATING_NOT_ELIGIBLE`, `RATING_DWELL_NOT_ELAPSED`, `RATING_WINDOW_EXPIRED`, `RATING_ALREADY_EXISTS`, `RATING_EDIT_WINDOW_CLOSED`, `RATING_SUPPLEMENT_TOO_SOON`, `RATING_SUPPLEMENT_LIMIT_REACHED`, `RATING_BODY_NOT_SANITIZED`, `RATING_TOO_MANY_PHOTOS`, `RATING_NOT_FOUND`, `RATING_FORBIDDEN`. Per the "Informative but Safe" guideline, dwell/window/supplement errors return the relevant unlock timestamp (e.g., `activeFrom`, next-supplement-allowed-at) so the UI can render a countdown. Messages live in `gimlee-ratings/src/main/resources/i18n/ratings/messages.properties` (+ `messages_pl.properties`), registered in `spring.messages.basename`.

### Front-end touchpoints (from `gimlee-ui` AGENTS.md)

- **User Spaces / profile**: card-based ratings list (no "soulless tables"), **separate seller-score and buyer-score** summaries + histograms (per `repKind`), progressive image loading for review photos, `Markdown` component for sanitized bodies + appended supplement blocks.
- **Post-purchase**: a "Rate your counterparty" affordance surfaces from `GET /ratings/pending`; before the dwell elapses the UI shows a "you can review this on <date>" countdown (from `activeFrom`); a global "you have N reviews to write" badge can be fed via the `/api/session/init` decorators.
- **Edit/supplement UX**: an "edit" affordance is shown only while inside the free-edit window (countdown to `editableUntil`); once frozen it switches to an "add a follow-up" affordance that unlocks after the cooldown and disables at `max-supplements`.
- **Verified-purchase** badge on each review; rater shown by **User-Space handle** with `GeometricAvatar` fallback.
- **Admin**: ratings (and individual supplements) appear in the existing report/moderation views (most-reported triage); hide/restore actions map to `ReportResolution`.

## Alternatives Considered

1. **Store ratings inside `gimlee-purchases`** — rejected: violates SRP and blocks non-purchase contexts (Requirement #3) and risks circular deps once community/Q&A consume it.
2. **Compute aggregates on read (aggregation pipeline per profile view)** — rejected for the hot path: profile pages are high-traffic and public; a denormalized `aggregates` doc keeps reads O(1). The sweeper provides a self-healing safety net.
3. **Client-submitted `rateeId`/snapshot** — rejected: forgeable, enables review fraud. Server-minted eligibility is the trust anchor.
4. **Live join to the ad for the item info** — rejected: ads are mutable/deletable; the immutable snapshot is required for appeals (Requirement #2).
5. **One bidirectional rating record per transaction** — rejected: complicates moderation (one party's content can't be independently hidden) and the per-author "latest written" query. Two independent directed ratings are cleaner.

## Consequences

### Positive
- **Extensible by design**: new rating contexts (community help, Q&A) require only a new `contextType` constant, a `RatingContextStrategy`, and an eligibility-granting listener — zero changes to `gimlee-ratings` core.
- **Trustworthy**: ratings are provably tied to a real, completed interaction; snapshots make moderation/appeals unambiguous.
- **Fast reads**: dedicated indexes for "latest received" and "latest by author"; O(1) reputation summaries.
- **Moderation-native**: reuses the proven `gimlee-support` report pipeline and soft-delete model; no parallel moderation stack.
- **Privacy-aware**: layered projections keep transaction internals off public surfaces.

### Negative / Trade-offs
- **Eventual consistency** of aggregates: a crash between rating write and aggregate update leaves drift until the sweeper reconciles (bounded, self-healing).
- **Snapshot duplication**: item data is duplicated per rating (acceptable; immutability is the goal, and field names are abbreviated to limit size).
- **Reciprocal-bias risk**: mutual visibility can encourage retaliatory ratings. The team decided **not** to ship blind double-rating in v1 (deferred); the dwell time partially dampens heat-of-the-moment retaliation in the meantime, and the strategy already reserves a hook for a future blind window.
- **Open-by-default identity**: attributing reviews to a User-Space handle (no anonymity in v1) is a deliberate transparency trade-off that may chill some honest negative feedback until an opt-in anonymization checkbox is added.
- **Cross-module wiring**: adding the module requires updating `settings.gradle.kts`, `Dockerfile`, and `spring.messages.basename` (easy to forget — called out explicitly).

### Future Extensibility

| Feature                         | How it plugs in                                                                 |
|---------------------------------|---------------------------------------------------------------------------------|
| Community-help ratings          | New `contextType="COMMUNITY_HELP"`; listener grants eligibility on a help event |
| Q&A answer ratings              | `contextType="QA_ANSWER"`, `subjectKind` could extend to `"ANSWER"`             |
| Rating an ad/listing (not user) | `subjectKind="AD"`; aggregates keyed by adId — no rating-core change            |
| "Helpful" votes                 | `hc` field already reserved; add a votes sub-collection + index                 |
| Blind reciprocal reveal (deferred per Ad.3) | Strategy flag + delayed `publishedAt`/visibility gate               |
| Rater anonymization (deferred per Ad.5)     | Per-rating `anonymous` flag honored by the public projection        |
| Seller reply analytics          | `rsp` already structured for response-rate metrics                              |
| AI/spam pre-moderation          | Hook before publish to set `status=HID` pending review                          |

## Resolved Decisions

The open questions from the initial draft have been resolved by the team as follows (and are now reflected throughout this ADR):

1. **Edit policy** → **Free-edit window then freeze then bounded supplements.** Unlimited edits within a configurable window after each touch (initial 10–30 min, default 30); after freeze the review is immutable, but the author may **append** supplements after a cooldown (default 1 week), capped at `max-supplements` (default 4). See *Edit / Freeze / Supplement lifecycle*.
2. **Dwell time** → **Adopted, initial value 7 days.** A rating cannot be submitted until `dwell-days` after completion (`activeFrom` on the eligibility). See *Dwell time*.
3. **Reciprocal (blind) visibility** → **Deferred** to a future iteration; only a strategy hook is reserved now.
4. **Aggregate scope** → **Separate buyer-reputation vs seller-reputation** aggregates, keyed by `(rateeId, repKind)`. See collection #3.
5. **Identity display** → **User-Space handle** in the initial phase (full transparency, "open-source" way); a per-rating anonymization opt-in is deferred. See *Context-aware Projections*.
6. **Eligibility expiry** → **Hard-delete** expired `PENDING` eligibilities (no `EXPIRED` tombstones); the sweeper performs the deletion. See *Eligibility expiry*.

Additionally, per the latest feedback: **server-side sanitization validation** of all user-submitted markdown (bodies, supplements, responses) is now a first-class requirement — the back-end independently verifies the content was properly sanitized by the front-end and rejects unsafe markup. See *Server-side sanitization validation*.

## Remaining Open Questions (for future refinement)
1. Exact initial value for `edit-window-minutes` (10 vs 30) — to be confirmed during implementation.
2. Whether supplements should themselves be score-bearing in any future context (currently text-only).
