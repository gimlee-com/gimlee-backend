# Chat Module Architecture

## Real-Time Messaging
The chat module leverages **Server-Sent Events (SSE)** for real-time delivery of messages and typing indicators. 

### Event Flow
1. **Internal Broadcasting**: When a message is sent or a typing indicator is triggered via `ChatService`, an `InternalChatEvent` is published using Spring's `ApplicationEventPublisher`.
2. **Buffering**: `ChatEventBroadcaster` listens for these events and collects them in a thread-safe per-chat buffer.
3. **Flushing**: A scheduled task flushes these buffers every `gimlee.chat.sse.buffer-ms` (default 200ms) to all active `SseEmitter` instances for that chat. This batching reduces network overhead.

## Horizontal Scaling Considerations

### The "Sticky Chat" Requirement
The current implementation uses JVM-local memory for event buffering and `SseEmitter` management. In a multi-instance deployment, this presents a challenge: if User A and User B are in the same Chat 1 but connected to different server instances, they will not see each other's messages in real-time.

To scale this horizontally, one of the following strategies **MUST** be implemented:

1. **Sticky Chats (Consistent Hashing)**:
   - Configure the Load Balancer (e.g., Traefik or Cloud LB) to route requests based on the `chatId` in the URL path (`/api/chat/{chatId}/...`).
   - This ensures all participants of a specific chat always land on the same instance, allowing the in-memory broadcaster to function correctly.
   - Note: This is "Sticky Chat", not "Sticky Session".

2. **Distributed Event Bus**:
   - Replace the local `ApplicationEventPublisher` with a distributed mechanism like **Redis Pub/Sub** or **MongoDB Tailable Cursors**.
   - When an instance receives a message, it publishes it to the global bus. All instances subscribe to the bus and push the message to their local `SseEmitter`s if they have active subscribers for that `chatId`.

### Recommended Path
For initial scaling, **Option 1 (Sticky Chats)** is preferred as it requires no code changes and leverages infrastructure-level routing. Option 2 is more robust for very large scales but introduces external dependencies and additional latency.
