# HLD System Design — Practice Notes
### (Coupa L3 — 2026 Campus Prep)

**General approach for any HLD question (use this structure every time):**
1. **Clarify requirements** — functional (what it does) + non-functional (scale, latency, consistency vs availability).
2. **Back-of-envelope estimate** — rough traffic/storage numbers (shows maturity, even if approximate).
3. **High-level design** — draw boxes: client → gateway/LB → services → cache → DB.
4. **Deep dive** into 1-2 core components the interviewer probes.
5. **Discuss trade-offs & failure handling** — this is what separates a good answer from a great one.

---

# 1. API GATEWAY ⭐⭐⭐⭐⭐
*(Appeared directly in 2026 campus — highest priority)*

### What it is
A single entry point that sits between clients and backend microservices, handling cross-cutting concerns so individual services don't have to reimplement them.

```
                     ┌─────────────────┐
Client ───────────▶  │   API Gateway    │
                     └────────┬─────────┘
                              │
        ┌─────────────┬──────┴───────┬──────────────┐
        ▼             ▼              ▼              ▼
   Auth Service   Orders Service  Users Service  Inventory Service
```

### Core Responsibilities

**1. Routing**
- Maps incoming requests (by path/host/method) to the correct downstream microservice.
- e.g., `/api/orders/*` → Orders Service, `/api/users/*` → Users Service.
- Can do **path rewriting** (`/v1/orders` → internal `/orders`) and **API versioning** (`/v1/`, `/v2/` routed to different service versions).

**2. Authentication (& Authorization)**
- Validates the caller's identity **once**, at the edge, instead of every microservice reimplementing auth.
- Typically: validate JWT signature/expiry, or call an Auth Service to validate a session/API key.
- On success, gateway can inject a trusted identity header (e.g., `X-User-Id: 123`) so downstream services don't need to re-verify tokens.
- Can also enforce coarse-grained authorization (e.g., "only Admin role can hit `/admin/*`"), leaving fine-grained authorization to individual services.

**3. Rate Limiting**
- Protects backend services from being overwhelmed (by a single bad client or overall traffic spikes).
- Usually implemented with **token bucket** per API key/client, backed by Redis (`INCR` + `EXPIRE`, or Lua script for atomicity) — see Redis notes.
- Returns `429 Too Many Requests` with `Retry-After` header when exceeded.
- Can have tiered limits (free tier vs enterprise customer).

**4. Load Balancing**
- Gateway (or a component just behind it) distributes requests across multiple healthy instances of a downstream service.
- Algorithms: round robin, least connections, weighted (based on instance capacity).
- Works with **service discovery** (below) to know which instances are currently alive.

**5. Logging (& Monitoring/Tracing)**
- Centralized place to log every request/response (status code, latency, client ID) — huge for debugging and analytics since it's the single choke point all traffic passes through.
- Often injects/propagates a **correlation/trace ID** (`X-Request-Id`) so a single request can be traced across multiple downstream microservices (ties into distributed tracing — e.g., Jaeger, Zipkin).

**6. Caching**
- Gateway can cache responses for cacheable GET endpoints (e.g., product catalog) to reduce load on backend services — similar to a CDN but for API responses, often keyed by URL + query params + auth context.
- Respects `Cache-Control` headers from the origin service.

**7. Service Discovery**
- In a microservices world, service instances are ephemeral (scaled up/down, restarted, IPs change) — the gateway needs to know *where* each service currently lives.
- Approaches:
  - **Client-side discovery**: gateway queries a registry (e.g., **Consul, etcd, Eureka**) to get a live list of healthy instance IPs, then load-balances itself.
  - **Server-side discovery**: gateway forwards to a well-known internal load balancer/DNS name (e.g., Kubernetes Service) which handles routing to actual pods — this is the more common pattern in Kubernetes-based systems (gateway doesn't need to know pod IPs at all).

**8. Failure Handling**
- **Timeouts**: every downstream call has a timeout so one hanging service doesn't hang the gateway/all requests.
- **Circuit breaker**: if a downstream service is failing repeatedly, "trip" and fail fast (return cached/default response or a clean error) instead of piling up requests against a dying service — protects against cascading failure.
- **Retries**: retry idempotent calls (GET) with backoff; be careful retrying non-idempotent calls (use Idempotency-Key).
- **Fallback responses**: e.g., serve stale cached data or a degraded response instead of a hard failure when a non-critical downstream service is down.
- **Bulkheading**: isolate resources (thread pools/connection pools) per downstream service so one slow service can't exhaust resources needed for calls to other services.

### Deep-Dive Answer: "How would you design rate limiting in the gateway?"
1. Identify client by API key / user ID / IP.
2. Use Redis token bucket: each client has a bucket refilled at rate `r` tokens/sec, max capacity `b`. Each request consumes 1 token.
3. Implement atomically via a Lua script (check + decrement in one round trip, avoids race conditions between concurrent requests).
4. On empty bucket → `429` + `Retry-After` computed from refill rate.
5. Make limits configurable per-tier (via a config service or DB, cached in the gateway).

### Interview Summary Line
> "An API Gateway centralizes cross-cutting concerns — auth, rate limiting, routing, logging, caching — so individual microservices stay focused on business logic. The key design tension is that the gateway itself must not become a single point of failure or bottleneck, so it's typically deployed as multiple stateless, horizontally-scaled instances behind a load balancer, with all shared state (rate limit counters, cache) externalized to Redis."

---

# 2. URL SHORTENER ⭐⭐⭐⭐

### Requirements
- Given a long URL, generate a short unique code (`https://sho.rt/aZ9kT`).
- Redirect short URL → original URL with low latency.
- Scale: potentially millions of URLs created, billions of redirects (read-heavy: redirects >> creations, often 100:1 or more).

### High-Level Design
```
Client ─▶ API Gateway ─▶ Shortener Service ─┬─▶ Cache (Redis) ─▶ Return cached long URL
                                             └─▶ Database (if cache miss)
```

### Short Code Generation (core deep-dive)

**Option A: Base62 encoding of an auto-incrementing ID**
- DB (or a distributed ID generator) issues a unique numeric ID (e.g., `125`).
- Encode that ID in Base62 (`[0-9a-zA-Z]`, 62 characters) → short, URL-safe string.
```python
BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

def encode_base62(num):
    if num == 0:
        return BASE62[0]
    chars = []
    while num > 0:
        num, rem = divmod(num, 62)
        chars.append(BASE62[rem])
    return ''.join(reversed(chars))

encode_base62(125)  # -> "cB" (example)
```
- Pros: short, no collisions (IDs are unique by construction), reversible.
- Cons: sequential IDs are guessable/predictable (someone could enumerate all URLs) and a single auto-increment counter is a scaling bottleneck/single point of contention at very high write volume → mitigate with **distributed ID generation** (e.g., Twitter Snowflake-style IDs, or pre-allocated ID ranges per server).

**Option B: Random string + collision check**
- Generate a random 6-7 character string (Base62) → check DB if it already exists → retry on collision.
- Pros: not predictable/guessable. Cons: extra DB read per generation, and collision probability grows as the keyspace fills up (need enough entropy — 7 chars of Base62 ≈ 62^7 ≈ 3.5 trillion combinations, plenty for most systems).

**Option C: Hash-based (MD5/SHA-256 of the long URL, truncated)**
- Deterministic — same long URL always maps to same short code (dedup benefit).
- Must handle collisions (truncated hash can collide) — append a counter/salt and rehash on collision.

**Most common real answer**: Base62(auto-increment ID) with a distributed ID generator (like Snowflake) for scale — good balance of simplicity, uniqueness guarantee, and no collision-retry overhead.

### Database Design
```
Table: url_mappings
- id (PK, bigint, auto-increment or snowflake)
- short_code (indexed, unique)
- long_url
- created_at
- expires_at (optional)
- user_id (optional, if URLs are owned by users)
- click_count (optional, if not tracked separately)
```
- NoSQL (e.g., DynamoDB/Cassandra) is a common real-world choice — simple key-value access pattern (`short_code → long_url`), easy to shard/scale horizontally, no complex joins needed.

### Redirect Flow
1. Client hits `GET /aZ9kT`.
2. Service checks **cache** (Redis) for `aZ9kT → long_url`. 
3. **Cache hit** → respond with `301 Moved Permanently` (or `302 Found` if you want to track every click / allow the mapping to change) + `Location: <long_url>` header.
4. **Cache miss** → look up in DB → populate cache → respond.

**301 vs 302 for redirects (classic follow-up):**
- `301` = browser/CDN can cache the redirect permanently → faster on repeat visits, but you lose the ability to track every single click (browser may not even hit your server again) and can't change the destination later.
- `302` = "temporary" — browser hits your server every time → lets you log every click (analytics) and change the mapping later. Most URL shorteners actually use `302` for this reason, despite the tradeoff in caching benefit.

### Caching
- Cache-aside pattern (see APIs notes) — hot/popular URLs stay in Redis; long tail falls through to DB.
- TTL or LRU eviction for cache since not all URLs are equally popular (classic Zipfian access distribution — a small % of URLs get most of the traffic).

### Scaling
- **Read-heavy** system → cache aggressively, use read replicas for the DB.
- **Horizontal scaling** of the shortener service (stateless — any instance can generate/redirect).
- Shard the DB by `short_code` hash if a single DB node can't hold all mappings (though a URL shortener's data is small per-record, so at extreme scale it's more about read QPS than storage).
- Use a CDN/edge cache for extremely hot redirects if using `301`s.

### Interview Summary Line
> "The core design decision is short-code generation — I'd use Base62 encoding of a distributed, unique ID (like Snowflake) to avoid both collisions and a single-point-of-contention counter. Since redirects vastly outnumber creations, I'd cache aggressively with Redis in front of the DB, and use 302 redirects to preserve click analytics at the cost of some CDN-level caching."

---

# 3. PAYMENT SYSTEM ⭐⭐⭐⭐

### Requirements
- Process a payment request reliably, exactly-once (or at-least-once with safe retries), with strong consistency (money can't be lost or double-charged).
- Non-functional: **consistency > availability** for the core transaction (unlike most systems, you'd rather reject a payment than risk double-processing).

### High-Level Design
```
Client ─▶ API Gateway ─▶ Payment Service ─┬─▶ Validation
                                           ├─▶ Idempotency Check (Redis/DB)
                                           ├─▶ Transaction (DB, ACID)
                                           ├─▶ Payment Provider (external, e.g., Stripe/bank)
                                           └─▶ Async: Notification/Ledger update (via Message Queue)
```

### 1. Payment Request Flow
1. Client sends payment request with an **Idempotency-Key** (generated client-side, unique per logical payment attempt).
2. Gateway/service checks: has this idempotency key been seen before?
   - **Yes** → return the previously stored result (don't reprocess).
   - **No** → proceed.
3. **Validate** the request (amount > 0, valid currency, account exists, sufficient balance/authorization, fraud checks).
4. Begin a **DB transaction**: debit payer, credit payee (or mark as pending), write a transaction record — atomically.
5. Call the external **payment provider** (e.g., card network, bank) to actually move money.
6. Update the transaction status based on provider response (`SUCCESS`/`FAILED`/`PENDING`).
7. Emit an event (via message queue) for downstream consumers — e.g., send receipt email, update ledger/accounting system, trigger order fulfillment — **asynchronously**, decoupled from the critical payment path.

### 2. Idempotency (core deep-dive — most likely interview focus)
- **Why critical**: network timeouts are common; client doesn't know if a payment succeeded or failed and will retry — without idempotency, this can double-charge a customer.
- **Implementation**:
```python
def process_payment(request):
    key = request.idempotency_key
    existing = db.get_idempotency_record(key)
    if existing:
        return existing.stored_response  # safe replay, no reprocessing

    # Use a DB unique constraint on idempotency_key to prevent race conditions
    # if two requests with the same key arrive concurrently.
    with db.transaction():
        record = db.insert_idempotency_record(key, status="PROCESSING")
        result = charge_payment_provider(request)
        db.update_transaction(request.order_id, status=result.status, amount=request.amount)
        db.update_idempotency_record(key, status="DONE", response=result)
    return result
```
- Store idempotency keys with a reasonable expiry (e.g., 24h) — long enough to cover realistic retry windows, short enough to not grow unbounded.
- Use a **DB unique constraint** on the idempotency key (not just a cache check) to handle the race condition of two concurrent requests with the same key arriving at the same time.

### 3. Transaction / Consistency
- Core money-movement operations must be **ACID** — use a relational DB (Postgres/MySQL) with proper transactions for the ledger, not eventual-consistency NoSQL, for the source-of-truth balance/transaction records.
- **Double-entry bookkeeping pattern** (common in real payment systems): every transaction writes two ledger entries (debit one account, credit another) that must always net to zero — makes auditing/reconciliation much easier and catches bugs.
- If crossing multiple services/DBs (e.g., wallet service + order service), consider the **Saga pattern**: break the transaction into a sequence of local transactions with compensating actions if a later step fails (since distributed transactions/2PC are complex and often avoided at scale).

### 4. Failure & Retry Handling
- **Timeout calling the payment provider**: the money might have actually moved even if you didn't get a response! Don't assume failure → mark as `PENDING`/`UNKNOWN` and reconcile via a status-check API call to the provider (most providers, e.g., Stripe, support querying "what happened to charge X") rather than blindly retrying (which could double-charge).
- **Retries**: only safe with the idempotency key passed through to the provider too (most payment providers support this exact pattern — e.g., Stripe's own `Idempotency-Key` header).
- **Dead-letter queue**: if async post-processing (e.g., sending a receipt) fails repeatedly, move it to a DLQ for manual investigation instead of blocking or endlessly retrying.
- **Reconciliation job**: a periodic background job that compares internal transaction records against the payment provider's records to catch and fix any inconsistencies (belt-and-suspenders for a domain where correctness really matters).

### 5. Validation
- Input validation (amount format, currency codes).
- Business validation (sufficient balance, account not frozen, spending limits).
- Fraud/risk checks (velocity checks — too many payments too fast, unusual amount/location).
- **Do validation before starting the DB transaction** where possible, to fail fast and cheaply.

### Interview Summary Line
> "The two things I'd emphasize are idempotency and consistency. Idempotency-Key with a DB unique constraint prevents double-processing on retries. For consistency, I'd use ACID transactions with a double-entry ledger for the source of truth, and treat provider-call timeouts as 'unknown' rather than 'failed' — reconciling via status checks rather than blind retries, since incorrectly retrying a payment is worse than a slightly slower response."

---

# 4. YOUTUBE-LIKE SYSTEM ⭐⭐⭐

### Requirements
- Users upload videos; other users can view/stream them at various qualities.
- Highly read-heavy (views >> uploads), globally distributed viewers, large binary files.

### High-Level Design
```
Uploader ─▶ API Gateway ─▶ Upload Service ─▶ Blob Storage (raw video, e.g., S3)
                                    │
                                    ▼
                          Message Queue (upload event)
                                    │
                                    ▼
                         Video Processing Service
                    (transcoding to multiple resolutions/formats)
                                    │
                                    ▼
                          Blob Storage (processed renditions) ──▶ CDN
                                    │
                                    ▼
                          Metadata DB (title, description, status, owner)

Viewer ─▶ CDN (video segments) + API (metadata/recommendations)
```

### 1. Upload
- Client uploads raw video file — for large files, use **chunked/multipart upload** directly to blob storage (e.g., pre-signed S3 URLs) rather than routing the whole file through the application server (avoids the app server becoming a bottleneck/memory hog).
- On upload completion, an event is published (message queue) to kick off processing asynchronously — uploader doesn't wait for transcoding to finish.

### 2. Storage
- **Blob/object storage** (S3, GCS) for the actual video files — not a traditional DB (videos are large, unstructured binary blobs).
- Store multiple renditions per video (different resolutions: 240p/480p/720p/1080p/4K, different formats/codecs) to support adaptive bitrate streaming.

### 3. Processing (Transcoding)
- A dedicated **Video Processing Service** (often a pool of workers pulling jobs off a queue) picks up the raw upload and:
  - Transcodes into multiple resolutions/bitrates.
  - Generates thumbnails.
  - Splits into small segments (e.g., using HLS/DASH — video split into a few-second chunks) to support **adaptive bitrate streaming** (player switches quality on the fly based on network conditions).
  - Runs content moderation/copyright checks.
- This is **asynchronous and horizontally scalable** — many workers process many videos in parallel; a queue (Kafka/SQS) buffers the workload and absorbs spikes.
- Update video status in metadata DB (`UPLOADING → PROCESSING → READY → FAILED`) so the UI can reflect state.

### 4. CDN
- Processed video segments are pushed to/pulled by a **CDN**, which caches them at edge locations close to viewers worldwide.
- Viewers stream from the nearest edge server → low latency, reduced load on origin storage, handles massive concurrent viewership (e.g., a viral video) without hitting origin storage repeatedly.
- Adaptive bitrate: client player requests a manifest file (list of available quality segments) and dynamically picks segments based on current bandwidth.

### 5. Metadata
- A separate DB stores structured metadata: title, description, uploader, upload date, view count, likes, tags, processing status, pointers (URLs) to the CDN-hosted video renditions.
- This DB is queried far more often than the video files themselves change → good candidate for caching (Redis) for hot/trending video metadata, and read replicas for scaling reads.
- **View count** is a classic "hot key" problem — incrementing a counter on every single view for a viral video causes huge write contention on one DB row. Common solution: buffer increments in memory/Redis and batch-flush to the DB periodically (eventual consistency is fine for view counts).

### Interview Summary Line
> "The key insight is separating the upload/processing pipeline (asynchronous, write-heavy but low-frequency) from the viewing path (synchronous, extremely read-heavy, latency-sensitive). Raw and processed videos live in blob storage, transcoding happens asynchronously via a queue and worker pool, and actual viewing is served almost entirely from a CDN so the origin servers/DB are barely touched during playback."

---

# 5. E-COMMERCE SYSTEM ⭐⭐⭐

### Requirements
- Browse products, manage a cart, place an order, pay, track/manage inventory.
- Mixed read/write pattern: browsing is read-heavy, checkout is write-heavy and consistency-sensitive (can't oversell inventory).

### High-Level Design
```
Client ─▶ API Gateway ─┬─▶ Product Service ──▶ Product DB + Cache (catalog, read-heavy)
                        ├─▶ Cart Service ─────▶ Cart DB / Redis (session-like, ephemeral)
                        ├─▶ Order Service ────▶ Order DB (ACID transaction)
                        ├─▶ Inventory Service ▶ Inventory DB (needs strong consistency)
                        └─▶ Payment Service ──▶ (see Payment System design above)
```

### 1. Product (Catalog)
- Read-heavy — product listings, search, filters.
- Cache aggressively (Redis/CDN for images) — product data changes infrequently relative to how often it's read.
- Search often uses a dedicated search engine (Elasticsearch) rather than the primary DB, for fast filtering/full-text search/faceted search.

### 2. Cart
- Semi-ephemeral, user-specific state — good fit for Redis (fast, TTL-based expiry for abandoned carts) or a DB table keyed by user ID, depending on durability needs (do you need the cart to survive a Redis restart? Often yes for a good UX, so many real systems persist cart to DB and use Redis as a read cache).
- Needs to handle: add/remove items, quantity updates, price recalculation (prices can change between adding to cart and checkout — validate at checkout time).

### 3. Order
- Created when checkout is initiated — represents a durable, immutable record of "what was ordered, at what price, at what time."
- **Order + Inventory + Payment often need to be coordinated as a single logical transaction** across services → this is the classic distributed transaction problem.
  - Common pattern: **Saga** — a sequence of local transactions with compensating actions:
    1. Reserve inventory (decrement available stock, or mark as "reserved").
    2. Charge payment.
    3. Confirm order.
    4. **If payment fails** → compensating action: release the reserved inventory back to available stock.
    5. **If inventory reservation fails** (out of stock) → don't even attempt payment.

### 4. Inventory (core deep-dive — classic tricky part)
- **The core challenge**: preventing overselling when multiple customers try to buy the last unit of a product simultaneously.
- **Approach 1 — Pessimistic locking**: `SELECT ... FOR UPDATE` on the inventory row during checkout — locks the row so concurrent transactions wait/fail. Simple, strongly consistent, but can hurt throughput under high contention (e.g., flash sales).
- **Approach 2 — Optimistic concurrency**: read the current stock + a version number; on update, use `UPDATE inventory SET stock = stock - 1, version = version + 1 WHERE product_id = X AND version = <read_version> AND stock > 0`. If 0 rows affected (someone else updated first), retry the read-modify-write. Better throughput than pessimistic locking in most cases.
- **Approach 3 — Reserve then confirm**: on "add to cart"/"begin checkout," place a short-lived **reservation** (e.g., Redis with TTL) that decrements "available" stock without a full DB write; if checkout isn't completed within the TTL, the reservation expires and stock is released automatically. Reduces DB contention during the "browsing/considering" phase; only a real DB transaction happens at final payment confirmation.
- **Flash-sale scale answer**: use Redis atomic decrement (`DECR`) as a fast-path gatekeeper for "is there stock left at all" before even hitting the DB, since Redis handles very high write throughput far better than a relational DB row under contention.

### 5. Payment
- Delegate to the Payment System design above (idempotency, ACID transaction, provider call, failure handling as its own subsystem).

### Interview Summary Line
> "The interesting part of e-commerce HLD is coordinating Order, Inventory, and Payment as a single logical unit without a true distributed transaction — I'd use a Saga pattern: reserve inventory first (cheap to compensate), then charge payment, then confirm the order, with a compensating 'release inventory' step if payment fails. For inventory itself, I'd combine a fast Redis-based reservation/TTL layer to absorb flash-sale traffic with a final consistent DB check (optimistic concurrency) at actual payment time to guarantee we never oversell."

---

## Quick Comparison Table (for fast recall before the interview)

| System | Read vs Write | Core Challenge | Key Technique |
|---|---|---|---|
| API Gateway | N/A (infra layer) | Cross-cutting concerns without becoming a bottleneck | Stateless instances + externalized state (Redis) |
| URL Shortener | Read-heavy (redirects) | Unique short code generation at scale | Base62(distributed ID) + aggressive caching |
| Payment System | Write-critical, low volume relative to reads | Exactly-once processing, correctness | Idempotency-Key + ACID + reconciliation |
| YouTube-like | Extremely read-heavy (views) | Serving huge binary content globally, fast | Async transcoding + CDN + blob storage |
| E-commerce | Mixed; write-critical at checkout | Preventing overselling under concurrency | Saga pattern + optimistic concurrency/Redis reservation |

---

## General "Trade-off Vocabulary" to Use in Every Answer
- **Consistency vs Availability** (CAP-flavored thinking): payments/inventory lean consistency; product catalog/view counts lean availability+eventual consistency.
- **Synchronous vs Asynchronous**: critical path (charge the customer) is sync; side effects (send email, update analytics) should be async via a queue.
- **Latency vs Throughput**: caching/CDN trade some staleness for latency; batching trades some latency for throughput.
- Always state **why** you're choosing SQL vs NoSQL for a given component (structured/transactional data with relations → SQL; simple key-value, high write throughput, flexible schema → NoSQL).

**Final tip**: For L3, interviewers care more about *whether you can explain trade-offs clearly and defend a decision* than whether you produce a "perfect" architecture. Always narrate your reasoning out loud.
