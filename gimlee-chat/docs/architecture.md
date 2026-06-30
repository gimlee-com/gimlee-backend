# Chat Module Architecture

## Real-Time Messaging
The chat module leverages **Server-Sent Events (SSE)** for real-time delivery of messages and typing indicators. 

### Event Flow
1. **Internal Broadcasting**: When a message is sent or a typing indicator is triggered via `ChatService`, an `InternalChatEvent` is published using Spring's `ApplicationEventPublisher`.
2. **Buffering**: `ChatEventBroadcaster` listens for these events and collects them in a thread-safe per-chat buffer.
3. **Flushing**: A scheduled task flushes these buffers every `gimlee.chat.sse.buffer-ms` (default 200ms) to all active `SseEmitter` instances for that chat. This batching reduces network overhead.

## Horizontal Scaling Considerations

### The Challenge
The current implementation uses JVM-local memory for event buffering and `SseEmitter` management. In a multi-instance deployment, this presents a challenge: if User A and User B are in the same Chat 1 but connected to different server instances, they will not see each other's messages in real-time.

This is correct only while the application runs as a **single instance** (`app_node_count = 1`, the current default). It becomes a problem the moment we scale to two or more app nodes.

### Chosen Strategy: Distributed Event Bus (Redis Pub/Sub)
The accepted approach — see **[ADR 0005: Real-Time Chat Horizontal Scaling](../../docs/adr/0005-real-time-chat-horizontal-scaling.md)** — is a **distributed event bus** built on **Redis Pub/Sub**, introduced behind a pluggable `ChatEventTransport` abstraction:

- Each instance keeps its **local** `SseEmitter` registry, but cross-instance delivery flows through Redis (keyed by `chatId`).
- When an instance receives a message it publishes to the bus; every instance with active subscribers for that `chatId` pushes to its local emitters.
- **Any participant can connect to any node** — no load-balancer affinity and no client contract change. The LB stays a simple round-robin.
- **MongoDB remains the source of truth** for history. SSE clients reconnect and back-fill missed messages via cursor pagination, so Redis Pub/Sub's fire-and-forget nature only affects the *live* path.

The abstraction keeps `gimlee-chat` business-agnostic and extractable: the default `in-process` transport preserves today's single-node behaviour, and the Redis transport is enabled via configuration (`gimlee.chat.sse.transport=redis`) when scaling out.

### Alternatives Considered
- **Sticky Chats (consistent hashing)**: route by `chatId` so all participants of a chat land on the same instance. Rejected as the primary path — Traefik cannot natively hash on a URL path segment, true per-chat affinity requires client/routing changes, and long-lived SSE connections suffer on hash-ring rebalancing (deploys) and hot chats. See ADR 0005 for the full analysis.
- **MongoDB tailable cursors**: a no-new-dependency bus, but lower throughput and added load on the primary DB.

### Future Evolution
At very large scale (or as broader cross-cutting eventing needs emerge), the bus is expected to evolve from Redis Pub/Sub to a **dedicated broker (Kafka / NATS)** for durability, replay, and high-volume fan-out. Because delivery goes through the `ChatEventTransport` abstraction, this is a transport swap rather than a chat-module rewrite.
