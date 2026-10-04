# Operational Runbook: Redis Chat Scaling & Pub/Sub Operations

## 1. Overview & Architecture Context
In the Gimlee architecture, `gimlee-chat` achieves horizontal scaling across multiple application instances (`app_node_count > 1`) using a distributed event bus implemented on top of **Redis Pub/Sub** (per **ADR 0005**).

- **Channel Name**: `gimlee.chat.events`
- **Role**: Ephemeral real-time broadcast of message and typing events across all application nodes.
- **Source of Truth**: MongoDB (`gimlee-chat-messages` collection) remains the durable authoritative store. Redis Pub/Sub only carries real-time notifications.
- **Fan-out Flow**: When any application node receives a message, it persists the document in MongoDB and publishes an event payload to `gimlee.chat.events`. All application nodes listening to the channel buffer the event and flush it to local participant `SseEmitter` connections within 200ms (`gimlee.chat.sse.buffer-ms`).

---

## 2. Capacity Planning & Memory Sizing

### Memory Allocation
Because Redis Pub/Sub messages are fire-and-forget and not stored in data structures (such as lists or streams), memory utilization is primarily driven by:
1. Redis internal metadata and client connection tracking.
2. Subscriber client output buffers (`client-output-buffer-limit`).

- **Configured Memory Limit**: `256mb` (`maxmemory 256mb`)
- **Eviction Policy**: `noeviction` (`maxmemory-policy noeviction`)
  - *Rationale*: Since Redis does not store persistent keys for chat Pub/Sub, `noeviction` prevents unexpected eviction behaviors while ensuring deterministic memory bounds.

### Client Output Buffer Limits
To prevent a slow network link or overloaded app node from exhausting Redis host memory, the following client output buffer configuration is enforced for Pub/Sub connections:
```text
client-output-buffer-limit pubsub 32mb 8mb 60
```
- **Hard Limit (32MB)**: If a subscriber's buffer exceeds 32MB, Redis immediately disconnects that client.
- **Soft Limit (8MB / 60s)**: If a subscriber's buffer remains over 8MB for more than 60 consecutive seconds, Redis terminates the connection.

---

## 3. Monitoring & Key Metrics

### Health Checks via Redis CLI
Execute these commands on the database host (`role=db`) inside the Redis container:
```bash
docker exec -it gimlee-redis redis-cli -a "$REDIS_PASSWORD" ping
# Response: PONG
```

### Inspecting Active Channels and Subscribers
```bash
# List all active channels matching pattern
docker exec -it gimlee-redis redis-cli -a "$REDIS_PASSWORD" PUBSUB CHANNELS "gimlee.chat.*"

# Inspect subscriber count for chat events channel
docker exec -it gimlee-redis redis-cli -a "$REDIS_PASSWORD" PUBSUB NUMSUB gimlee.chat.events
```
*Expected Value*: `PUBSUB NUMSUB gimlee.chat.events` should report a subscriber count equal to the number of active `app` nodes running the application with `gimlee.chat.sse.transport=redis`.

### Key Metrics to Monitor via Prometheus / Grafana
Metrics are scraped via `redis_exporter` on port `9121`:
- `redis_up`: Indicates whether the Redis instance is operational (must be `1`).
- `redis_connected_clients`: Total active connections (should reflect app nodes + monitoring scraper).
- `redis_pubsub_channels`: Number of active Pub/Sub channels (should be at least `1` when chat is active).
- `redis_memory_used_bytes` / `redis_memory_max_bytes`: Memory consumption ratio (should remain well below 80%).
- `redis_connected_slaves`: Replication status (if configured).

---

## 4. Troubleshooting & Incident Response

### Issue 1: Real-time Messages Not Received on Remote Nodes
**Symptoms**: A user connected to Node A sends a message. Node A shows the message immediately, but a recipient connected to Node B only sees it after refreshing the page.

**Diagnostic Steps**:
1. Check subscriber count on Redis:
   ```bash
   docker exec -it gimlee-redis redis-cli -a "$REDIS_PASSWORD" PUBSUB NUMSUB gimlee.chat.events
   ```
   If the count is less than the active app node count, Node B is not subscribed.
2. Check Node B application logs:
   ```bash
   docker logs gimlee-api | grep -i "redis"
   ```
   Look for connection errors or subscription failures (`RedisConnectionFailureException`).
3. Verify Redis transport configuration in `application.yaml` on Node B:
   - Ensure `gimlee.chat.sse.transport` is set to `redis`.
   - Ensure `spring.data.redis.host`, `spring.data.redis.port`, and `spring.data.redis.password` match the database node.
4. Verify network connectivity from Node B to the database node on port `6379`.

### Issue 2: Redis Outage / Unavailability
**Symptoms**: Redis server crashes, restarts, or becomes unreachable over the network.

**Impact & Graceful Degradation**:
- **Persistence Unaffected**: Chat messages continue to be stored safely in MongoDB. HTTP endpoints (`POST /chat/{id}/messages`) return `200 OK` without throwing errors to clients.
- **Log Warnings**: Application logs will emit warnings: `Failed to publish chat event to Redis transport: ...`.
- **Client Recovery**: While live SSE delivery is temporarily paused, clients that reconnect or poll the forward history endpoint (`GET /chat/{id}/messages?afterId=...`) will retrieve all stored messages seamlessly.

**Recovery Action**:
1. Check Redis container status on the `db` host:
   ```bash
   docker ps -a --filter name=gimlee-redis
   ```
2. Inspect Redis logs:
   ```bash
   docker logs --tail 100 gimlee-redis
   ```
3. Restart Redis if needed:
   ```bash
   docker start gimlee-redis
   ```
4. Spring Boot's connection factory automatically retries and reconnects to Redis once available, restoring the Pub/Sub subscription without requiring an application restart.

### Issue 3: Dropped SSE Connections During Deployments
**Symptoms**: Clients lose connection during rolling updates of app nodes.

**Expected Behavior & Client Protocol**:
1. When an application node shuts down, it gracefully completes active SSE streams.
2. Clients detect the connection termination and initiate an automatic reconnection backoff.
3. The load balancer routes the client to an available surviving app node.
4. Immediately after reconnecting the SSE stream, the client must invoke:
   ```http
   GET /chat/{conversationId}/messages?afterId={lastReceivedMessageId}
   ```
   This retrieves any messages dispatched while the client was reconnecting.
