# APIs / Web + System Design (HLD) — Interview Prep Notes
### (Coupa L3-focused)

---

# PART 1: APIs / WEB

## 1. REST (Representational State Transfer)

### Resources
- Everything is modeled as a **resource** — a noun, not a verb (e.g., `users`, `orders`, `invoices`), identified by a unique URI.
- Bad: `/getUser?id=1` (verb-based, RPC-style)
- Good: `/users/1` (resource-based)

### Endpoints
- An endpoint = a URL + HTTP method combination that maps to an action on a resource.

```
GET    /users          -> list all users
GET    /users/1        -> get user with id 1
POST   /users          -> create a new user
PUT    /users/1        -> replace user 1 entirely
PATCH  /users/1        -> partially update user 1
DELETE /users/1        -> delete user 1
```

- **Nested resources** (relationships): `/users/1/orders` -> all orders belonging to user 1.

### HTTP Methods & Status Codes
Covered in depth in your networking notes — recap table:

| Method | Purpose | Idempotent |
|---|---|---|
| GET | Read | Yes |
| POST | Create | No |
| PUT | Replace | Yes |
| PATCH | Partial update | No (usually) |
| DELETE | Remove | Yes |

Common status codes to know cold: `200, 201, 204, 301, 400, 401, 403, 404, 409, 422, 429, 500, 502, 503`
- **204 No Content**: success but nothing to return (common on DELETE).
- **409 Conflict**: e.g., duplicate resource, version conflict.
- **422 Unprocessable Entity**: syntactically correct but semantically invalid (e.g., validation error).
- **429 Too Many Requests**: rate limit exceeded.

### Statelessness
- **Core REST constraint**: the server stores **no client session state** between requests. Every request must contain all information needed to process it (auth token, params, etc.).
- **Why it matters**:
  - Enables **horizontal scaling** — any server instance can handle any request (no "sticky sessions" needed).
  - Simplifies server design and improves reliability (server crash doesn't lose client "conversation state").
  - Client (or a shared store like Redis/DB) is responsible for holding state, not the individual server instance.

**Interview one-liner:** "Statelessness means each request is self-contained. The server doesn't remember you between calls — that's what makes REST APIs horizontally scalable, since any server can serve any request."

---

## 2. HTTP: GET vs POST, PUT vs PATCH, Idempotency

### GET vs POST
| | GET | POST |
|---|---|---|
| Purpose | Retrieve data | Create/submit data |
| Body | No (data in URL/query params) | Yes |
| Cacheable | Yes | No (by default) |
| Idempotent | Yes | No |
| Bookmarkable | Yes | No |
| Data visibility | Visible in URL (not for sensitive data) | Hidden in body |

### PUT vs PATCH
- **PUT** = replace the *entire* resource. If you omit a field, it may be wiped/nulled.
- **PATCH** = apply a *partial* update — only send the fields that changed.

```json
// PUT /users/1  (must send the FULL object)
{ "name": "Ravi", "email": "ravi@example.com", "age": 30 }

// PATCH /users/1  (send only what changed)
{ "age": 31 }
```

### Idempotency (VERY important for interviews)
- **Definition**: An operation is idempotent if performing it multiple times has the same effect as performing it once.
- GET, PUT, DELETE → idempotent by design.
- POST → NOT idempotent by default (calling it twice creates two resources).
- **Why it matters in real systems**: Networks are unreliable — a client may retry a request after a timeout without knowing if the first one succeeded. If the operation is idempotent, retrying is *safe*. If not, you risk duplicate side effects (e.g., double-charging a customer, creating duplicate purchase orders).

**How to make POST idempotent (common real interview follow-up):**
- Use an **Idempotency-Key** header — client generates a unique key per logical operation; server stores the result of the first request against that key, and returns the same result for any retry with the same key without redoing the side effect.

```http
POST /payments
Idempotency-Key: 7b3f2a1c-... 
{ "amount": 500, "orderId": "PO-1234" }
```

Server-side pseudocode:
```python
def handle_payment(request):
    key = request.headers.get("Idempotency-Key")
    cached = redis.get(f"idem:{key}")
    if cached:
        return cached  # return the same response as before, don't reprocess
    result = process_payment(request.body)
    redis.set(f"idem:{key}", result, ex=86400)  # cache for 24h
    return result
```

---

## 3. Authentication

### Session-based Authentication
- User logs in → server creates a **session** (stored server-side, e.g., in memory/Redis/DB) → server sends a **session ID** to the client via a cookie.
- Every subsequent request includes that cookie; server looks up the session ID to identify the user.
- **Stateful** — server must store and look up session data. Harder to scale horizontally without a shared session store (e.g., Redis) or sticky sessions.

### JWT (JSON Web Token)
- **Stateless** authentication — the token itself carries the user's identity/claims, signed by the server.
- Structure: `header.payload.signature` (Base64-encoded, dot-separated).
  - **Header**: algorithm + token type (e.g., `HS256`, `JWT`).
  - **Payload**: claims (e.g., `userId`, `role`, `exp` — expiration).
  - **Signature**: `HMACSHA256(base64(header) + "." + base64(payload), secret)` — ensures the token wasn't tampered with.
- Server verifies the signature on each request — no DB/session lookup needed → scales well.
- Sent typically via `Authorization: Bearer <token>` header.

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEyM30.4f8s...
```

**Session vs JWT (classic question):**
| | Session | JWT |
|---|---|---|
| State | Stateful (server stores it) | Stateless (self-contained) |
| Scalability | Needs shared store for multi-server | Scales easily, no lookup needed |
| Revocation | Easy (delete session server-side) | Hard (must blacklist or use short expiry) |
| Size | Small (just an ID) | Larger (carries claims) |
| Typical use | Traditional web apps | APIs, microservices, mobile apps |

### OAuth 2.0 Basics
- **Authorization framework** — lets a third-party app access a user's resources on another service *without* getting the user's password (e.g., "Sign in with Google").
- Key roles:
  - **Resource Owner** — the user.
  - **Client** — the app requesting access.
  - **Authorization Server** — issues tokens (e.g., Google's OAuth server).
  - **Resource Server** — hosts the protected data (e.g., Google Contacts API).
- **Flow (Authorization Code Grant — most common for web apps)**:
  1. Client redirects user to the Authorization Server's login/consent screen.
  2. User logs in and approves access; Authorization Server redirects back with an **authorization code**.
  3. Client exchanges the code (+ client secret) for an **access token** (and optionally a **refresh token**).
  4. Client uses the access token to call the Resource Server's APIs.
  5. When the access token expires, client uses the **refresh token** to get a new one without re-prompting the user.

**OAuth vs JWT (common confusion to clarify in interview):** "OAuth is an *authorization protocol* (framework for granting access); JWT is just a *token format*. OAuth access tokens are often (but not always) implemented as JWTs."

---

## 4. API Concepts

### Request / Response Anatomy
```http
POST /api/users?verbose=true HTTP/1.1
Host: api.example.com
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "Ravi",
  "email": "ravi@example.com"
}
```
```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/users/123

{
  "id": 123,
  "name": "Ravi",
  "email": "ravi@example.com"
}
```

- **Headers**: metadata about the request/response (not the actual data) — e.g., `Content-Type`, `Authorization`, `Cache-Control`, `Accept`.
- **Body**: the actual payload — used in POST/PUT/PATCH (and optionally DELETE); not typically used in GET.
- **Query parameters**: key-value pairs after `?` used for filtering, sorting, pagination — do NOT identify a specific resource.
  - e.g., `/orders?status=pending&page=2&limit=20`
- **Path parameters**: part of the URL path, used to identify a *specific* resource.
  - e.g., `/orders/456` → `456` is the path parameter (order ID).

**Quick distinction:** "Path params identify *which* resource; query params *filter/modify* the request (optional, don't change the resource identity)."

---

## 5. Reliability

### Retries
- When a request fails (timeout, 5xx error), the client can retry.
- **Best practice**: use **exponential backoff with jitter** to avoid overwhelming a recovering server ("retry storm" / thundering herd).

```python
import time, random

def call_with_retry(fn, max_retries=5):
    for attempt in range(max_retries):
        try:
            return fn()
        except TransientError:
            wait = min(2 ** attempt + random.uniform(0, 1), 30)  # exponential backoff + jitter
            time.sleep(wait)
    raise Exception("Max retries exceeded")
```
- Only retry **idempotent** operations safely (or use an Idempotency-Key for non-idempotent ones like POST).

### Timeouts
- Every network call should have a timeout — never wait indefinitely.
- Types: **connection timeout** (time to establish connection) vs **read timeout** (time waiting for response after connected).
- Prevents resource exhaustion (threads/connections stuck waiting forever) when a downstream service is slow/dead.

### Rate Limiting
- Restricts how many requests a client can make in a time window — protects the API from abuse/overload.
- Common algorithms:
  - **Fixed window**: N requests per fixed time window (e.g., 100/min) — simple but can allow bursts at window boundaries.
  - **Sliding window**: smooths out the fixed-window boundary issue.
  - **Token bucket**: tokens added at a fixed rate into a bucket; each request consumes a token; allows short bursts up to bucket size. (Most common in practice — e.g., AWS API Gateway.)
  - **Leaky bucket**: requests processed at a constant fixed rate, queued if bursty.
- On exceeding the limit → respond with `429 Too Many Requests` (+ `Retry-After` header).

### Idempotency (reliability angle)
- Already covered above — critical when combined with retries: a client retry after a timeout should not cause duplicate side effects (e.g., a purchase order created twice).

### Error Handling
- Use meaningful HTTP status codes + a consistent error response shape:
```json
{
  "error": {
    "code": "INVALID_INPUT",
    "message": "Email field is required",
    "requestId": "abc-123"
  }
}
```
- Distinguish **client errors (4xx)** — don't retry without fixing the request — from **server errors (5xx)** — safe to retry (often transient).
- **Circuit breaker pattern**: after repeated failures to a downstream service, "trip" the circuit and fail fast for a cooldown period instead of hammering a struggling service (used heavily in microservices — e.g., Netflix Hystrix / resilience4j).

---

# PART 2: SYSTEM DESIGN / HLD (Basic-to-Intermediate, for L3)

## 6. Fundamentals

| Component | Role |
|---|---|
| **Client/Server** | Client requests, server processes and responds — the basic request/response model. |
| **Load Balancer** | Distributes incoming traffic across multiple server instances (algorithms: round robin, least connections, IP hash) — improves availability & scalability. |
| **Reverse Proxy** | Sits in front of servers, forwards client requests to the appropriate backend; can also do SSL termination, caching, compression (e.g., Nginx). Hides backend topology from clients. |
| **API Gateway** | A single entry point for all client requests to backend microservices — handles routing, auth, rate limiting, request/response transformation, logging (e.g., Kong, AWS API Gateway). |
| **Application Server** | Runs the actual business logic (e.g., Node.js/Java/Python app). |
| **Database** | Persistent storage — SQL (structured, ACID, relations) vs NoSQL (flexible schema, horizontal scaling — e.g., MongoDB, Cassandra, DynamoDB). |
| **Cache** | Fast, temporary storage (in-memory) to reduce load on DB/backend and reduce latency (e.g., Redis, Memcached). |
| **Message Queue** | Enables asynchronous, decoupled communication between services (e.g., Kafka, RabbitMQ, SQS) — producer pushes a message, consumer processes it independently. Useful for buffering spikes, retries, decoupling. |
| **CDN (Content Delivery Network)** | Geographically distributed servers caching static content (images, JS, CSS, videos) close to users — reduces latency, offloads origin server (e.g., Cloudflare, Akamai, CloudFront). |

**Reverse Proxy vs API Gateway (common confusion):** "A reverse proxy mainly does routing/load balancing/SSL termination. An API Gateway does that *plus* higher-level API concerns — auth, rate limiting, request transformation, aggregating multiple backend calls. In practice, API gateways are often built on top of reverse proxy technology."

---

## 7. Scaling

### Vertical Scaling ("Scale Up")
- Add more resources (CPU/RAM) to a single machine.
- Pros: simple, no code changes. Cons: hardware limits, single point of failure, downtime to upgrade.

### Horizontal Scaling ("Scale Out")
- Add more machines/instances behind a load balancer.
- Pros: near-limitless scaling, fault tolerance (one node dying doesn't kill the system). Cons: needs stateless services + load balancing + more operational complexity.

### Stateless Services
- Prerequisite for effective horizontal scaling.
- No server-specific data stored in memory between requests — any instance can serve any request.
- Session/state is externalized to a shared store (DB, Redis) rather than kept in the app server's memory.

### Replication
- Copying data across multiple database nodes.
- **Master-Replica (Leader-Follower)**: writes go to master, reads can be distributed across replicas → improves read scalability & provides failover.
- Types: synchronous (consistency, slower) vs asynchronous (faster, risk of replica lag / stale reads).

### Sharding (Horizontal Partitioning)
- Splitting a large dataset across multiple database instances ("shards"), each holding a subset of the data (e.g., by user ID range, hash of a key, geography).
- Improves write scalability (each shard handles a fraction of the total load) — unlike replication which mainly helps reads.
- Challenges: cross-shard joins/transactions are hard, rebalancing when adding/removing shards, choosing a good shard key (avoid hotspots).

**Replication vs Sharding (important distinction):** "Replication = same data copied across nodes (for availability & read scaling). Sharding = different data split across nodes (for write scaling & storage capacity)."

---

## 8. Caching

### Cache-Aside (Lazy Loading) — most common pattern
1. App checks cache first.
2. **Cache hit** → return cached data.
3. **Cache miss** → read from DB → write result into cache → return to caller.

```python
def get_user(user_id):
    user = redis.get(f"user:{user_id}")
    if user:
        return user  # cache hit
    user = db.query("SELECT * FROM users WHERE id = %s", user_id)
    redis.set(f"user:{user_id}", user, ex=3600)  # cache miss -> populate, TTL 1hr
    return user
```
- Pros: only requested data gets cached (efficient). Cons: first request always misses (cold start); possible staleness until TTL expires or explicit invalidation.

### Write-Through
- Every write goes to the cache **and** the DB synchronously (cache is always up to date).
- Pros: cache never stale. Cons: extra write latency (every write pays the cost twice).

### Write-Back (Write-Behind)
- Write goes to cache first, and is asynchronously flushed to the DB later (batched).
- Pros: very fast writes. Cons: risk of data loss if cache crashes before flush — needs durability strategy (e.g., persistence, replication).

### TTL (Time To Live)
- Every cache entry has an expiration time after which it's automatically evicted/considered stale.
- Balances freshness vs cache hit rate — shorter TTL = fresher but more DB load; longer TTL = better performance but staler data.

### Cache Invalidation ⭐ (commonly asked — "one of the two hard problems in CS")
Strategies:
1. **TTL-based (passive)**: let it expire naturally — simplest, but can serve stale data until expiry.
2. **Write-through invalidation (active)**: when data changes, update/delete the cache entry immediately as part of the write path.
   ```python
   def update_user(user_id, data):
       db.update("users", user_id, data)
       redis.delete(f"user:{user_id}")  # invalidate; next read repopulates (cache-aside)
   ```
3. **Event-driven invalidation**: DB change triggers an event (e.g., via CDC/Kafka) that tells the cache layer to invalidate relevant keys — used in distributed/microservices systems where multiple services might cache the same data.
4. **Versioning/cache-busting**: include a version number in the cache key (e.g., `user:123:v2`); bump version on update so old key naturally becomes unused (self-expires from LRU eventually).

### Cache Consistency
- **Strong consistency**: cache and DB always match (harder, usually requires write-through + invalidation on every write, or accepting extra latency).
- **Eventual consistency**: cache may briefly lag behind DB (acceptable for most read-heavy, non-critical data — e.g., product descriptions, view counts).
- Trade-off: **the CAP-like tension between performance and staleness** — most systems accept eventual consistency for cache because strict consistency defeats the purpose of caching (speed).

---

## 9. Redis

### Why Redis?
- In-memory data store → extremely fast (sub-millisecond) reads/writes.
- Supports rich data structures beyond simple key-value: Strings, Hashes, Lists, Sets, Sorted Sets, Streams, Bitmaps, HyperLogLog.
- Supports TTL/expiration natively — great fit for caching.
- Single-threaded event loop for commands → avoids race conditions on individual operations, atomic by default.
- Supports persistence (RDB snapshots, AOF logs) if durability is needed, though it's primarily used as a fast, volatile store.
- Supports Pub/Sub, and features like Lua scripting for atomic multi-step operations.

### What Can Redis Store / Be Used For?

**1. Cache**
- Classic cache-aside pattern as shown above; also used for caching computed/aggregated results, session data, rendered HTML fragments.

**2. Session Store**
- Store session data (`session:<sessionId>` → user data) with a TTL matching session expiry — enables horizontal scaling of app servers since session isn't tied to one server's memory.
```python
redis.set(f"session:{session_id}", json.dumps(user_data), ex=1800)  # 30 min TTL
```

**3. Rate Limiting**
- Common implementation: **fixed window counter** using `INCR` + `EXPIRE`:
```python
def is_rate_limited(user_id, limit=100, window=60):
    key = f"rate:{user_id}"
    count = redis.incr(key)
    if count == 1:
        redis.expire(key, window)  # set TTL only on first request in window
    return count > limit
```
- Or **token bucket** using Redis + Lua script for atomicity (avoids race conditions between check and decrement).

**4. Distributed Locks (basic)**
- Used to ensure only one process/instance performs a critical action at a time across a distributed system (e.g., only one server should process a scheduled job).
- Basic approach — `SET key value NX PX <ttl>`:
  - `NX` = only set if key doesn't exist (atomic "acquire lock if free").
  - `PX <ttl>` = auto-expire the lock after a timeout (prevents deadlock if the holder crashes without releasing).
```python
lock_acquired = redis.set("lock:job123", "worker-1", nx=True, px=30000)  # 30s TTL
if lock_acquired:
    try:
        do_critical_work()
    finally:
        redis.delete("lock:job123")  # release (ideally with a Lua script checking ownership first)
```
- **Caveat to mention in interview**: a naive `DEL` can accidentally delete another process's lock if the original lock expired and was re-acquired by someone else — production systems use a unique token per lock holder and a Lua script to check-and-delete atomically, or use a library like **Redlock** for stronger guarantees across multiple Redis nodes.

---

## 10. ⭐⭐ VERY IMPORTANT — Explicitly asked in Coupa 2026 L3

### Q: What happens if Redis goes down?

Structure your answer around **impact + mitigation**:

**If Redis is used as a cache (cache-aside):**
- Not catastrophic — it's a *cache*, not the source of truth. App falls back to hitting the database directly on every request.
- **Impact**: increased latency (no more fast cache hits) and a sudden spike in DB load — this can be dangerous if the DB wasn't sized to handle full traffic (**"thundering herd" / cache stampede** on recovery too, when Redis comes back empty and everything floods in to repopulate it at once).
- **Mitigations**:
  - Circuit breaker / fallback logic so the app degrades gracefully (maybe serve slightly stale data, or a simplified response) instead of crashing.
  - **Redis High Availability** setup: Redis Sentinel (automatic failover to a replica) or Redis Cluster (sharded + replicated) so a single node failure doesn't mean total cache loss.
  - Rate-limit/queue DB requests during cache recovery to avoid overwhelming it (e.g., request coalescing — only one request per key fetches from DB while others wait).

**If Redis is used as the primary session store or for distributed locks:**
- Impact is more serious: users could get logged out (sessions lost) or locks can't be acquired, blocking critical workflows.
- **Mitigations**: Redis persistence (AOF/RDB) so data can be recovered on restart; replication with Sentinel/Cluster for automatic failover; for locks, design the system to fail-safe (e.g., block a task rather than allow two conflicting instances to run) or use a quorum-based approach like Redlock.

**Strong interview-ready summary line:**
> "It depends on Redis's role. As a pure cache, the system should degrade gracefully — traffic falls back to the DB with higher latency, so I'd design for that fallback and protect the DB with circuit breakers and request coalescing. If Redis is the source of truth for sessions or locks, I'd use Sentinel/Cluster for HA and persistence to avoid data loss, since that failure is more critical."

---

### Q: How do you invalidate cache?

Structure your answer with **strategy options + trade-offs** (see also section 8 above):

1. **TTL / passive expiration** — simplest; accept a bounded staleness window. Good default for data that doesn't need to be perfectly fresh (e.g., product catalog).
2. **Active invalidation on write** — when the underlying data changes, explicitly `DELETE` (or update) the cache key as part of the write transaction/flow. Most common and reliable approach for cache-aside.
3. **Event-driven invalidation** — for distributed systems/microservices where multiple services cache the same data: publish a "data changed" event (via Kafka/pub-sub) that all interested caches subscribe to and invalidate on.
4. **Key versioning** — embed a version/timestamp in the key itself (`product:123:v5`); bumping the version on update automatically "orphans" old cache entries without needing to explicitly find and delete them (old ones just expire via TTL/LRU eviction naturally).
5. **Write-through** — avoid the invalidation problem altogether for critical data by keeping cache and DB in sync on every write (trade write latency for read consistency).

**Mention the trade-off explicitly (interviewers like this):**
> "The core trade-off is consistency vs performance/simplicity. TTL-based invalidation is simple but risks serving stale data for up to the TTL window. Active invalidation on write is more consistent but adds coupling — every write path must remember to invalidate the cache, which is a common source of bugs if missed. For frequently-changing, consistency-sensitive data, I'd combine both: invalidate on write as the primary mechanism, with a short TTL as a safety net in case an invalidation is missed."

---

## 11. Quick-Fire Q&A (APIs + HLD)

**Q: Why is REST called "stateless"? Doesn't the server track anything?**
A: The server doesn't track *client session state* between requests. It can still have its own internal state (DB), but each request must carry everything needed to process it (auth token, params) — no reliance on server "remembering" prior requests.

**Q: Client sends a POST and doesn't get a response (timeout) — is it safe to retry?**
A: Not inherently, since POST isn't idempotent — you might create a duplicate resource. Safe way: use an Idempotency-Key so the server recognizes and safely handles the retry.

**Q: Load balancer vs API gateway vs reverse proxy — how do they layer together?**
A: Typically: Client → CDN (static assets) → Load Balancer (distributes traffic) → API Gateway (auth, rate limiting, routing to microservices) → Application Servers → Cache/DB. A reverse proxy (like Nginx) is often the mechanism implementing the load balancer/gateway layer.

**Q: When would you choose sharding over replication?**
A: When a single node can't hold all the data or handle all the writes (storage/write bottleneck) — sharding splits data across nodes. Replication is for read scaling and availability/failover, not for solving a "too much data for one node" problem.

**Q: How would you rate-limit a public API used by many customers?**
A: Token bucket per API key/customer (allows short bursts, smooths average rate), stored in Redis with `INCR`+`EXPIRE` or a Lua script for atomicity; return `429` with a `Retry-After` header when exceeded; consider tiered limits for different customer plans.

---

## 12. Coupa-Specific Framing

Since Coupa is a **B2B procurement/spend-management SaaS**, expect scenario questions like:

- *"Design an API for creating a purchase order that's safe against duplicate submissions from a flaky network."* → Idempotency-Key + PUT semantics.
- *"How would you make sure two approvers don't approve/reject the same invoice at the same time?"* → Distributed lock (Redis `SET NX PX`) or optimistic concurrency (version field + conditional update).
- *"A vendor integration calls our API and sometimes gets 503s — how do you handle it on the client side?"* → Retry with exponential backoff + jitter, circuit breaker, respect `Retry-After`.
- *"How do you keep vendor/customer data fresh in cache while under heavy read load?"* → Cache-aside + active invalidation on write + short TTL safety net (this maps directly to the two "VERY IMPORTANT" Redis questions above — they're clearly core to how Coupa's platform is architected).

**Bottom line for L3**: You're not expected to design a full distributed system from scratch — you're expected to reason clearly about trade-offs (consistency vs performance, simplicity vs correctness) and connect textbook concepts to realistic API/service scenarios.
