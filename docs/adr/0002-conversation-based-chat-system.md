# ADR 0002: Conversation-Based Chat System

## Status
Accepted

## Context
Gimlee needs a chat feature between buyer and seller for specific orders. The existing `gimlee-chat` module provides raw message infrastructure (SSE broadcasting, archiving, typing indicators, cursor pagination) but has no concept of conversations, participants, or access control — anyone who knows a `chatId` can read/write messages.

Requirements:
1. Private buyer-seller chat per purchase order.
2. Access control: only participants can read/write.
3. Conversation lifecycle: lock on order completion (read-only), archive later.
4. System messages for status changes (rendered client-side per locale).
5. **Critical constraint**: `gimlee-chat` must remain business-agnostic — a generic, reusable conversation/messaging library with zero knowledge of purchases, orders, or any Gimlee-specific domain.
6. Future extensibility for private multi-user chats and community rooms (Discord-like servers with multiple rooms).

## Decision

### Architecture: Decoupled Orchestration

```
gimlee-chat    → gimlee-common, gimlee-auth  (generic, extractable)
gimlee-api     → gimlee-chat, gimlee-purchases, gimlee-events  (Gimlee orchestration)
```

**gimlee-chat** is a generic conversation + messaging system that owns:
- Conversation model, ConversationService, ConversationRepository
- ChatService, ChatRepository, ChatEventBroadcaster
- ChatController, ConversationController
- ConversationPolicy interface (+ default impl)
- Chat-specific events: ConversationEvent, MessageSentEvent, InternalChatEvent
- Generic DTOs, outcomes, i18n

**gimlee-api** is the Gimlee-specific orchestration layer that owns:
- PurchaseConversationListener — reacts to PurchaseEvent → creates conversation
- PurchaseStatusMessageListener — reacts to PurchaseEvent → posts system messages
- PurchaseConversationLockListener — reacts to terminal PurchaseEvent → locks conversation
- OrderConversationFacadeController — GET /purchases/{purchaseId}/conversation
- GimleeConversationPolicy — type-specific rules (ORDER: max 2 participants)
- GimleeChatPrincipalProvider — implements auth identity extraction
- ConversationTypes / ConversationLinkTypes constants

### Key Design Decisions

1. **Type as plain String**: Conversation `type` is an opaque String, not an enum inside chat. Consumers define semantics (e.g., gimlee-api defines "ORDER", "PRIVATE"). Chat module never branches on type values.

2. **Pluggable policy via Spring @Order**: `ConversationPolicy` interface enables type-specific behavior. A `DefaultConversationPolicy` with `@Order(Int.MAX_VALUE)` provides sensible defaults; consumers override with higher priority.

3. **Universal ConversationEvent**: A single event class with `cause: String` and `details: Map<String, Any>` replaces per-lifecycle event classes. Known causes:
   - `"CREATED"` → details: `{ "participantUserIds": [...] }`
   - Future: `"USER_ADDED"` → details: `{ "addedUserIds": [...] }`
   - Future: `"USER_REMOVED"` → details: `{ "removedUserIds": [...] }`

4. **Structured system messages**: System messages store opaque `systemCode` + `systemArgs` map. Chat module stores/transports them without interpreting them. Text field is null. Rendering is the client's responsibility per locale.

5. **Linked entity model**: `linkType` + `linkId` are opaque metadata with a unique partial index ensuring idempotent creation for linked conversations.

6. **SSE by (conversationId, userId)**: Emitters tracked per user to enable permission revocation and conversation lock enforcement.

7. **Auth abstraction**: `ChatPrincipalProvider` interface in gimlee-chat abstracts identity extraction. Implemented by consumers using their own auth mechanism.

8. **Chat-owned events**: All events (`ConversationEvent`, `MessageSentEvent`, `InternalChatEvent`) are defined inside `gimlee-chat` as Spring ApplicationEvents, not in `gimlee-events`. This keeps the chat module self-contained.

### Data Model

#### Conversation (`gimlee-chat-conversations`)

| Domain Field     | DB Field | Type     | Description                                     |
|------------------|----------|----------|-------------------------------------------------|
| id               | _id      | ObjectId | Primary key                                     |
| type             | tp       | String   | Opaque type string (consumers define semantics) |
| participants     | pts      | Array    | List of ConversationParticipant objects          |
| ∟ userId         | uid      | String   | Participant's user ID                           |
| ∟ role           | rl       | String   | ParticipantRole short name ("MBR"/"OWN"/etc.)   |
| ∟ joinedAt       | jat      | Long     | Epoch micros                                    |
| linkType         | lti      | String?  | Opaque linked entity type (e.g., "PRC")         |
| linkId           | lid      | String?  | Opaque linked entity ID                         |
| status           | st       | String   | ConversationStatus short name ("ACT"/"LCK"/"ARC")|
| createdAt        | cat      | Long     | Epoch micros                                    |
| updatedAt        | uat      | Long     | Epoch micros                                    |
| lastActivityAt   | lat      | Long     | Epoch micros — updated on each message          |

#### ArchivedMessage additions

| New Domain Field | DB Field | Type    | Description                               |
|------------------|----------|---------|-------------------------------------------|
| authorId         | aid      | String  | Author's userId (stable identity)         |
| messageType      | mt       | String  | MessageType short name ("R"/"S")          |
| systemCode       | sc       | String? | Opaque system message code                |
| systemArgs       | sa       | Map?    | Opaque structured args for client rendering|

#### Enums

- `ConversationStatus`: ACTIVE("ACT"), LOCKED("LCK"), ARCHIVED("ARC")
- `ParticipantRole`: OWNER("OWN"), ADMIN("ADM"), MODERATOR("MOD"), MEMBER("MBR")
- `MessageType`: REGULAR("R"), SYSTEM("S")

#### Indexes (Flyway Migration V002)

```javascript
// Unique: one conversation per linked entity (idempotent creation)
db.getCollection("gimlee-chat-conversations").createIndex(
  { lti: 1, lid: 1 },
  { unique: true, partialFilterExpression: { lti: { $exists: true } } }
);

// List user's conversations sorted by last activity
db.getCollection("gimlee-chat-conversations").createIndex(
  { "pts.uid": 1, lat: -1 }
);

// Filter active conversations
db.getCollection("gimlee-chat-conversations").createIndex(
  { st: 1 },
  { partialFilterExpression: { st: "ACT" } }
);
```

### Event Flow

```
PurchaseService.purchase()  [gimlee-purchases]
  → publishes PurchaseEvent(CREATED)  [via gimlee-events]

PurchaseConversationListener  [gimlee-api]
  → @EventListener on PurchaseEvent(CREATED)
  → calls ConversationService.createConversation(type="ORDER", ...)
  → ConversationService publishes ConversationEvent(cause="CREATED")

PurchaseStatusMessageListener  [gimlee-api]
  → @EventListener on PurchaseEvent(status changes)
  → calls ChatService.sendSystemMessage(conversationId, systemCode, args)

PurchaseConversationLockListener  [gimlee-api]
  → @EventListener on PurchaseEvent(COMPLETE or FAILED_*)
  → calls ConversationService.lockConversation(conversationId)
  → ChatEventBroadcaster closes SSE emitters for locked conversation
```

### Access Control Flow

```
User → ChatController.sendMessage(conversationId, principal)
  → ConversationService.verifyWriteAccess(conversationId, userId)
     → Checks: conversation exists, user is participant, status is ACTIVE
     → Delegates to ConversationPolicy.allowMessageSend()
  → ChatService.sendMessage(conversationId, authorId, authorName, text)
  → ConversationService.updateLastActivity(conversationId)
```

- **ACTIVE**: participants can read and write
- **LOCKED**: participants can read, cannot write
- **ARCHIVED**: participants cannot read or write

### Configuration

```yaml
gimlee:
  chat:
    conversation:
      default-max-participants: 50
    order:
      lock-on-complete: true
      lock-on-failed: true
      system-messages-enabled: true
```

## Consequences

### Positive
- **Extractable**: `gimlee-chat` can be reused in any other project without modification — zero Gimlee business knowledge.
- **Extensible**: Adding private chats or community rooms requires no changes to the chat module — just new constants and policies in the consuming module.
- **Secure**: All message access goes through conversation verification. Raw messages cannot be read without being a participant.
- **Future-proof**: Universal `ConversationEvent` with `cause`+`details` avoids event class proliferation as new lifecycle events emerge.
- **Scalable design**: Sticky Chat load balancing by conversationId enables horizontal scaling of SSE.

### Negative
- **Two-hop lookup**: Every message send requires a conversation lookup for access verification (mitigated by Caffeine caching in future if needed).
- **Auth coupling**: `gimlee-chat` still depends on `gimlee-auth` for the `@Privileged` annotation. Full decoupling would require moving controllers to a wrapper layer.
- **No unique index in tests**: Flyway migrations run externally, so the unique `{lti, lid}` index is not enforced in integration tests. Sequential idempotency is tested but not concurrent race conditions.

### Future Extensibility

| Feature | How it plugs in |
|---------|----------------|
| Private chats | gimlee-api defines type "PRIVATE"; user creates via POST /conversations; no linked entity needed |
| Community rooms | New `gimlee-communities` module; type "COMMUNITY_ROOM"; linkType="COMMUNITY", linkId=communityId; custom policy for room rules |
| User invitation | Publish ConversationEvent(cause="USER_ADDED", details={addedUserIds:[...]}) |
| User removal | Publish ConversationEvent(cause="USER_REMOVED", details={removedUserIds:[...]}) |
| Distributed SSE | Replace JVM-local broadcaster with Redis Pub/Sub; conversation model unchanged |
| Full auth decoupling | Move controllers to gimlee-api as thin wrappers; chat becomes pure service layer |
