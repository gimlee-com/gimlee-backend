# ADR 0005: Real-Time Chat Horizontal Scaling Strategy

## Status
Accepted

## Context

The `gimlee-chat` module delivers real-time messages, typing indicators, and system
messages over **Server-Sent Events (SSE)**. The current implementation (`ChatEventBroadcaster`)
is **JVM-local**:

- `SseEmitter` instances are held in an in-process `ConcurrentHashMap<chatId, List<UserEmitter>>`.
- `InternalChatEvent`s are published via Spring's `ApplicationEventPublisher` (in-process only).
- A scheduled task (`flushBuffers`, every `gimlee.chat.sse.buffer-ms`, default 200ms) batches and
  pushes buffered events to the local emitters for each chat.

This works correctly only when **all participants of a given chat are connected to the same JVM**.

### Current deployment topology

```
Internet
  │  :443 (TCP passthrough, sticky_sessions = false)
  ▼
Hetzner Cloud LB (lb11)  ── label_selector role=app ──►  app nodes (app_node_count, default 1)
  ▼ (per node)
Traefik v3.6.5 (docker provider)  ──►  local gimlee-api container (:12060)
```

Two load-balancing layers exist, and **neither performs chat affinity today**:

1. **Hetzner Cloud LB** — L4 TCP passthrough on :443. State confirms `sticky_sessions: false`.
2. **Traefik** runs on each app node and only discovers containers on its own host, so it routes to
   the local `gimlee-api`. No `loadbalancer.sticky` / `consistenthashing` labels are configured
   (`infrastructure/ansible/playbooks/deploy-app.yml`).

**Why nothing is broken yet:** `app_node_count` defaults to **1**. With a single instance, in-memory
buffering is correct. The problem surfaces the moment we scale to **2+ app nodes**: if User A and
User B share Chat 1 but land on different instances, they will not see each other's events in
real time.

`gimlee-chat/docs/architecture.md` already flags this as a hard requirement for horizontal scaling
and sketches two strategies (Sticky Chat vs. distributed bus). This ADR exists to make that decision
deliberately, weigh the options, and record the chosen path before we increase `app_node_count`.

### Requirements & constraints

1. **Correctness**: every participant of a chat must receive every event in near real time,
   regardless of which instance they connect to.
2. **Long-lived connections**: SSE connections are persistent (infinite emitter timeout, 30s
   heartbeats). The solution must tolerate connection churn, node restarts, and graceful shutdown
   (the app runs with `server.shutdown=graceful`, 30s timeout; emitters are completed in
   `@PreDestroy`).
3. **Global platform, no off-peak**: load is continuous across time zones — no maintenance window
   can be assumed (see scheduling guidelines).
4. **Module purity**: `gimlee-chat` must remain business-agnostic and ideally extractable
   (per ADR 0002). Any new infrastructure dependency must be optional / pluggable.
5. **Operational simplicity**: we already run Traefik + CrowdSec + Mongo; adding moving parts has a
   real maintenance cost on small (`cx23`, 2 vCPU / 2 GB) nodes.
6. **Path-based identity**: chat identity lives in the URL path (`/api/chat/{chatId}/...`), which
   constrains some LB-affinity options (see Option B).

## Decision Drivers

- Expected concurrency (connections per chat, chats per node, total nodes) in the next 12–18 months.
- Tolerance for added latency on message fan-out.
- Willingness to introduce and operate a new stateful dependency (e.g. Redis).
- Need for cross-instance features beyond chat (presence, multi-room communities, notifications).

## Options Considered

### Option A — Status quo: single app instance (vertical scaling only)

Keep `app_node_count = 1` and scale the single node up (CPU/RAM) as needed.

**Pros**
- Zero code changes; in-memory broadcaster remains correct.
- Simplest possible operations.

**Cons**
- No horizontal redundancy — the app node is a single point of failure for *all* traffic, not just
  chat.
- Hard ceiling: SSE connections are cheap but not free (one Jetty thread / request context each).
- Does not satisfy the stated goal of horizontal scaling.

**Verdict**: acceptable only as the *current* state, not as a target architecture.

---

### Option B — Sticky Chat via load-balancer consistent hashing

Pin each `chatId` to a single instance at the LB layer, so all participants of a chat always reach
the same JVM. The in-memory broadcaster then "just works" unchanged.

**Mechanism / caveats**
- Traefik's `consistenthashing` load balancer hashes a **header, cookie, or client IP** — it
  **cannot natively hash on a URL path segment** like `{chatId}`. To key on chat we'd need the
  client to send `chatId` as a request **header** (e.g. `X-Chat-Id`), or introduce a small routing
  shim that promotes the path segment to a header.
- Critically, Traefik on each node only sees *local* containers. Consistent hashing across nodes
  must therefore live at a **shared upstream** — i.e. the Hetzner Cloud LB — which only offers
  IP/cookie stickiness (`sticky_sessions`), **not** chatId-based consistent hashing. So a pure
  Hetzner-LB solution can only do *per-client* stickiness, not *per-chat*.
- A workable variant: run a **single shared routing tier** (one Traefik/HAProxy fleet, not one per
  app node) configured with consistent hashing on `X-Chat-Id`, fronting all app nodes. This is a
  meaningful topology change from today's "Traefik-per-node".

**Pros**
- **No `gimlee-chat` code changes** to the broadcaster; preserves module purity.
- No new stateful dependency (no Redis/queue to operate).
- Lowest fan-out latency — events never leave the owning JVM.

**Cons**
- **Per-client stickiness ≠ per-chat correctness.** If two participants of one chat have different
  sticky keys (different IPs/cookies), they can still land on different nodes. True correctness needs
  *chatId* affinity, which forces the header shim + shared routing tier above.
- **Rebalancing pain**: when a node is added/removed or restarts (deploys!), the hash ring reshuffles
  and a fraction of chats migrate. In-flight SSE connections drop and must reconnect to the new
  owner. With long-lived connections and frequent deploys this is disruptive.
- **Uneven load / hot chats**: a single very active chat (or community room) is bound to one node and
  cannot be spread.
- **Couples the client contract** to scaling internals (clients must send `X-Chat-Id`).
- Blocks future cross-instance features (global presence, notifications) that also need a shared bus.

**Verdict**: cheapest to build *if* a per-client approximation were acceptable, but achieving real
correctness requires non-trivial routing changes and still leaves rebalancing/hot-chat problems.

---

### Option C — Distributed event bus via Redis Pub/Sub

Replace the in-process `ApplicationEventPublisher` fan-out with a shared **Redis Pub/Sub** channel
(e.g. channel-per-chat or a single channel keyed by `chatId`). Each instance keeps its **local**
`SseEmitter` registry, but events are broadcast through Redis so every instance with a subscriber for
that chat delivers locally.

```
send message → publish to Redis (chat:{chatId})
   every instance subscribed → push to its local SseEmitters for that chatId
```

**Pros**
- **Any participant can connect to any node** — no affinity, no client contract change. The LB stays
  a dumb round-robin (today's setup works as-is).
- Deploys/restarts only drop that node's own connections; survivors are unaffected and reconnects can
  land anywhere.
- Even load distribution; hot chats are naturally spread across all subscribed nodes.
- Foundation for other cross-instance features (presence, typing, push notifications).
- Pluggable: a `ChatEventTransport` interface keeps `gimlee-chat` pure — default = in-process
  (current behaviour), Redis impl enabled by config when scaling out.

**Cons**
- Introduces a **new stateful dependency** (Redis) to deploy, secure, monitor, and pool (per
  performance guidelines: pooled, configurable, shutdown-aware clients).
- **Fire-and-forget**: Redis Pub/Sub does not persist or replay. If an instance is briefly
  disconnected from Redis it misses messages during that window. Mitigation: SSE clients already
  reconnect and **back-fill from Mongo** via cursor pagination, so the persistent store remains the
  source of truth — Pub/Sub is only the *live* path.
- Small added fan-out latency (one network hop through Redis).
- Redis becomes a shared failure domain for *live* delivery (not for persisted history).

**Verdict**: best balance of correctness, operational simplicity, and future-proofing for our scale.

---

### Option D — Distributed bus via MongoDB Tailable Cursors (capped collection)

Use a capped collection + tailable cursor as the live event bus instead of Redis. Each instance tails
the collection and pushes matching events to local emitters.

**Pros**
- **No new infrastructure** — reuses the MongoDB we already run.
- Naturally persisted within the capped collection's window (some short replay possible).

**Cons**
- Tailable cursors are a niche, lower-throughput mechanism; capped-collection sizing is a blunt knob
  and back-pressure handling is awkward at high event rates.
- Adds sustained read load to the primary DB node (`cpx11`, 2 vCPU / 2 GB), competing with core
  workloads.
- More bespoke code and operational edge cases (cursor death, reconnection storms) than Redis Pub/Sub.

**Verdict**: attractive for "no new dependency", but worse throughput/operational profile than Redis.
A reasonable fallback if we are firmly committed to not running Redis.

---

### Option E — Dedicated message broker (Kafka / NATS / RabbitMQ)

Run a full broker as the event backbone.

**Pros**
- Highest throughput, durability, replay, and ecosystem; serves many future use cases beyond chat.

**Cons**
- Heavyweight to operate for current scale — overkill on 2 vCPU / 2 GB nodes.
- Largest operational and cognitive overhead; premature for present requirements.

**Verdict**: revisit only if event volumes and cross-cutting eventing needs grow substantially.

## Decision

**Adopt Option C (Redis Pub/Sub distributed event bus) as the target strategy for horizontal scaling
of real-time chat**, introduced behind a pluggable transport abstraction.

Rationale:

- It delivers **correctness without LB affinity**, avoiding Option B's rebalancing, hot-chat, and
  client-contract problems with long-lived SSE connections.
- It keeps `gimlee-chat` **pure and extractable**: the broadcaster keeps its local emitter registry;
  only the *cross-instance transport* is abstracted.
- MongoDB remains the **source of truth** for history (clients back-fill on reconnect), so Redis
  Pub/Sub's fire-and-forget nature is acceptable for the *live* path.
- It lays groundwork for future cross-instance features (presence, notifications, multi-room
  communities).

Until we actually need a second app node, we remain on **Option A** (`app_node_count = 1`,
in-process transport) — the Redis transport ships disabled by default and is switched on by config
when we scale out.

**Future evolution**: **Option E (a dedicated broker such as Kafka / NATS)** is the agreed
successor to Redis Pub/Sub once we reach very large scale or accumulate broader cross-cutting
eventing needs (durability, replay, high-volume fan-out). The `ChatEventTransport` abstraction is
specifically designed so this migration is a transport swap rather than a chat-module rewrite.

### Implementation sketch (non-binding)

1. **Introduce `ChatEventTransport` abstraction** in `gimlee-chat`:
   - `publish(chatId, event)` and `subscribe(handler)`.
   - `InProcessChatEventTransport` (default, `@Order` lowest priority) wraps current behaviour.
   - `RedisChatEventTransport` enabled via `gimlee.chat.sse.transport=redis`.
2. **`ChatEventBroadcaster`** publishes to the transport instead of relying solely on the local
   `ApplicationEventPublisher`; its `flushBuffers` continues to drive **local** emitters from events
   received via the transport.
3. **Redis client**: pooled and fully configurable (size, timeouts) per performance guidelines;
   declared shutdown-aware so connection threads release on graceful shutdown.
4. **Reconnect/back-fill**: confirm the SSE client reconnect flow back-fills missed messages from
   Mongo via existing cursor pagination, so a transient Redis gap cannot lose history.
5. **Config** (defaults preserve today's single-node behaviour):
   ```yaml
   gimlee:
     chat:
       sse:
         transport: in-process   # in-process | redis
         buffer-ms: 200
         heartbeat-ms: 30000
       redis:
         host: ...
         port: 6379
         pool-size: ...
         timeout-ms: ...
   ```
6. **Infra**: add a Redis instance (Ansible role + Terraform target + firewall rules scoped to
   `role=app`), with monitoring (node/redis exporter) consistent with existing observability.

## Scaling Considerations

- **Connection capacity**: SSE connections are long-lived; the practical ceiling per node is bounded
  by Jetty request threads and memory, not by the chosen transport. Size app nodes accordingly and
  scale **out** once a single node's connection/CPU budget is reached.
- **Proxy capacity is not the bottleneck**: Traefik v3 (fasthttp) comfortably handles tens of
  thousands of req/s — far beyond what a `cx23` JVM node can serve. Switching Traefik→HAProxy would
  buy marginal proxy efficiency at the cost of losing Traefik's Docker auto-discovery, ACME, and the
  CrowdSec/fingerprint plugins already in use. **Not recommended** as a scaling lever; the app tier
  saturates first.
- **Redis sizing**: Pub/Sub throughput is high and message payloads are small (batched every 200ms).
  A single small Redis instance covers early multi-node scale; cluster only if fan-out volume
  demands it.
- **Graceful deploys**: with Option C, rolling deploys drop only the restarting node's connections;
  clients reconnect to any node and back-fill from Mongo — far smoother than Option B's hash-ring
  reshuffles.
- **Hot chats / community rooms**: distributed bus spreads a busy chat across all subscribed nodes,
  unlike Option B which pins it to one.

## Consequences

### Positive
- Correct real-time delivery across any number of app instances with **no LB affinity** and **no
  client contract change**.
- `gimlee-chat` stays business-agnostic and extractable; transport is a pluggable concern.
- Single-node deployments are unaffected (default in-process transport).
- Establishes reusable cross-instance eventing for future features.

### Negative
- Adds Redis as a new operational dependency (deploy, secure, monitor, pool, shutdown-aware client).
- Live delivery gains one network hop of latency and a shared failure domain (history is unaffected —
  Mongo remains source of truth).
- Requires the SSE reconnect/back-fill path to be robust to brief live-bus gaps.

### Follow-ups
- Spike `RedisChatEventTransport` behind the abstraction; benchmark fan-out latency at target
  concurrency with Gatling (`gimlee-chat/src/gatling`).
- Verify SSE reconnect back-fill correctness under node restart.
- Add Redis to Ansible/Terraform with firewall scoping and monitoring before raising
  `app_node_count`.
- Update `gimlee-chat/docs/architecture.md` to reference this ADR as the chosen path.
