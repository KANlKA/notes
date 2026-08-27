# Interview Prep Notes: System Failure Handling + OS Fundamentals
### (Target: Coupa, L3-style rounds)

---

# PART 1 — DISTRIBUTED SYSTEMS: FAILURE MODES

The interviewer's mental model here is: **"Something in your stack just died. What happens next, and what did you design in advance so it doesn't take the whole system down?"**
Always answer in this shape: **Detect → Contain → Degrade gracefully → Recover → Prevent recurrence.**

---

## 1. Redis Failure

### What can fail
- Redis node crashes / OOMs
- Network partition between app and Redis
- Redis is up but slow (not "down", which is worse)

### Strategy

| Concern | Mechanism |
|---|---|
| Redis totally unreachable | **Fallback to DB** — read/write directly from source of truth |
| Key not in cache | **Cache miss** — read-through: fetch from DB, populate cache, return |
| Node dies | **Replication** — replica gets promoted |
| Automatic promotion | **Failover** — via Redis Sentinel or Redis Cluster |

### Read-through cache with fallback (Python)

```python
import redis
from redis.exceptions import RedisError

r = redis.Redis(host='cache-host', socket_timeout=0.2, socket_connect_timeout=0.2)

def get_user(user_id):
    try:
        cached = r.get(f"user:{user_id}")
        if cached:
            return deserialize(cached)
    except RedisError:
        # Redis down/unreachable -> don't crash, go to DB
        log.warning("Redis unavailable, falling back to DB")

    # Cache miss OR Redis down
    user = db.query("SELECT * FROM users WHERE id = %s", user_id)
    try:
        r.setex(f"user:{user_id}", 300, serialize(user))  # TTL = 5 min
    except RedisError:
        pass  # best-effort cache write; don't fail the request over this

    return user
```

**Key interview point:** set a **short socket timeout** on the Redis client. A slow-but-alive Redis is more dangerous than a dead one, because every request blocks waiting on it and you exhaust your app's thread/connection pool. This is why timeouts are the first line of defense, before retry/circuit breaker even come into play.

### Replication & Failover
- **Redis Sentinel**: monitors master + replicas, quorum-based decision, promotes a replica to master, updates clients via pub/sub.
- **Redis Cluster**: data sharded across nodes (hash slots), each shard has replicas; failover is per-shard.
- Talking point: replication is usually **asynchronous** in Redis → failover can lose the last few writes (a small window of data loss). If you need strong durability, that's a trade-off to explicitly call out.

---

## 2. Database Failure

### Strategy

| Concern | Mechanism |
|---|---|
| Transient failure (network blip, deadlock) | **Retry** (with backoff, idempotent operations only) |
| Primary DB down | **Replica** promoted to serve reads, eventually writes |
| Automatic promotion | **Failover** (managed by orchestrator like Patroni, RDS Multi-AZ, etc.) |
| Repeated failures | **Circuit breaker** — stop hammering a dead DB |

### Read replica pattern
```python
def get_order(order_id):
    # Reads go to replica (eventually consistent, but scales reads)
    return replica_db.query("SELECT * FROM orders WHERE id=%s", order_id)

def place_order(order):
    # Writes MUST go to primary
    return primary_db.execute("INSERT INTO orders ...", order)
```

**Interview trap to mention:** replica lag. If a user places an order then immediately reads it back from a replica, they may not see it (**read-your-writes** consistency problem). Common fixes: read from primary right after a write, or route "recent write" reads to primary for N seconds, or use session-level consistency tokens.

### Circuit breaker (see Section 3 for full code — same pattern applies to DB calls)

---

## 3. Service Failure (Downstream Microservice Calls)

This is the **most commonly asked** section at L3. Know **Timeout → Retry → Backoff → Circuit Breaker** as a layered defense, in that order.

### 3.1 Timeout
Never call a remote service without a timeout. Without it, a hung downstream service can hang your entire thread pool (cascading failure).

```python
response = requests.get(url, timeout=(0.5, 2))  # (connect_timeout, read_timeout)
```

### 3.2 Retry (only for idempotent operations, or with idempotency keys)
```python
def retry(fn, attempts=3):
    for i in range(attempts):
        try:
            return fn()
        except TransientError:
            if i == attempts - 1:
                raise
    
```

### 3.3 Exponential Backoff + Jitter
Plain retries in lockstep across many clients cause a **thundering herd** on recovery. Jitter fixes this.

```python
import random, time

def call_with_backoff(fn, max_attempts=5, base=0.2, cap=5.0):
    for attempt in range(max_attempts):
        try:
            return fn()
        except TransientError:
            if attempt == max_attempts - 1:
                raise
            sleep_time = min(cap, base * (2 ** attempt))
            sleep_time = random.uniform(0, sleep_time)  # full jitter
            time.sleep(sleep_time)
```

### 3.4 Circuit Breaker
Prevents a struggling downstream service from being repeatedly hit while it's down — gives it room to recover, and fails fast for the caller instead of piling up timeouts.

**States:** `CLOSED → OPEN → HALF_OPEN → CLOSED`

```python
import time

class CircuitBreaker:
    def __init__(self, fail_threshold=5, reset_timeout=30):
        self.fail_threshold = fail_threshold
        self.reset_timeout = reset_timeout
        self.failure_count = 0
        self.state = "CLOSED"
        self.opened_at = None

    def call(self, fn, *args, **kwargs):
        if self.state == "OPEN":
            if time.time() - self.opened_at > self.reset_timeout:
                self.state = "HALF_OPEN"   # allow a trial request
            else:
                raise Exception("Circuit OPEN — failing fast")

        try:
            result = fn(*args, **kwargs)
        except Exception as e:
            self._record_failure()
            raise
        else:
            self._record_success()
            return result

    def _record_failure(self):
        self.failure_count += 1
        if self.failure_count >= self.fail_threshold:
            self.state = "OPEN"
            self.opened_at = time.time()

    def _record_success(self):
        self.failure_count = 0
        self.state = "CLOSED"
```

**Diagram to describe verbally:**
```
CLOSED --(failures >= threshold)--> OPEN
OPEN --(reset_timeout elapsed)--> HALF_OPEN
HALF_OPEN --(trial call succeeds)--> CLOSED
HALF_OPEN --(trial call fails)--> OPEN
```

**How the 4 combine in one request:** Timeout bounds how long you wait → Retry handles a transient blip → Backoff spaces retries out → Circuit breaker stops retrying altogether once the service looks systemically down, and periodically probes it.

---

## 4. Worker Failure (Background Jobs / Async Processing)

### Strategy

| Concern | Mechanism |
|---|---|
| Worker crashes mid-job | Message stays in **queue** (not acknowledged) until re-delivered |
| Job fails due to transient error | **Retry** with backoff |
| Job fails repeatedly (poison message) | **Dead-letter queue (DLQ)** — quarantine it, alert, don't block the queue |

### Conceptual flow
```
Producer -> Queue -> Worker
                        |
             success -> ACK (remove from queue)
             failure -> NACK -> requeue (with retry count)
                        |
             retry_count > max -> move to DLQ
```

### Example (pseudo-code, e.g. SQS/RabbitMQ style)

```python
def process_message(msg):
    try:
        handle(msg.body)
        msg.ack()
    except TransientError:
        if msg.retry_count < MAX_RETRIES:
            msg.retry_count += 1
            queue.requeue(msg, delay=backoff(msg.retry_count))
        else:
            dead_letter_queue.send(msg)
            msg.ack()  # remove from main queue, it's now in DLQ
            alert_oncall(msg)
    except PermanentError:
        # non-retryable (bad payload, validation failure)
        dead_letter_queue.send(msg)
        msg.ack()
```

**Key points to say out loud:**
- **At-least-once delivery** is the norm (SQS, RabbitMQ, Kafka default) → your job handler **must be idempotent**, because the same message can be delivered twice (e.g., worker crashes after processing but before ACK).
- **Visibility timeout** (SQS) / **prefetch + ack** (RabbitMQ): if a worker dies mid-processing without acking, the message becomes visible again after a timeout and another worker picks it up.
- DLQ prevents one **poison message** from blocking the whole queue and burning infinite retries.

---

## 5. Cache Invalidation

> "There are only two hard things in Computer Science: cache invalidation and naming things." — worth dropping this if it fits naturally, not forced.

| Strategy | How it works | Trade-off |
|---|---|---|
| **TTL** | Key auto-expires after N seconds | Simple, but data can be stale for up to TTL duration |
| **Explicit invalidation** | On write, actively `DEL` or update the cache key | Fresh data, but easy to miss a code path and leave stale cache |
| **Event-driven invalidation** | A write publishes an event (e.g., Kafka/pub-sub); a consumer invalidates relevant cache keys | Decouples services, scales well, but adds infra complexity and eventual-consistency lag |

```python
# Explicit (write-through) invalidation
def update_user(user_id, data):
    db.execute("UPDATE users SET ... WHERE id=%s", user_id)
    redis.delete(f"user:{user_id}")   # invalidate; next read repopulates
```

```python
# Event-driven
def update_user(user_id, data):
    db.execute("UPDATE users SET ... WHERE id=%s", user_id)
    event_bus.publish("user.updated", {"user_id": user_id})

# separate consumer, possibly in another service
def on_user_updated(event):
    redis.delete(f"user:{event['user_id']}")
```

**Trade-off to mention:** TTL-only is simplest and most resilient to bugs (self-heals), but weaker consistency. Explicit/event-driven gives freshness but risk is a missed invalidation path leaving stale data indefinitely — many teams do **both**: explicit invalidation as the primary path, TTL as a safety net.

---

## 6. Cloud / IP Changes — Why Not to Hard-code IPs

In cloud/container environments, IPs are **ephemeral**: pods restart with new IPs, autoscaling adds/removes instances, deployments replace instances. Hard-coded IPs break the moment topology changes, and give you zero elasticity or failover.

### The fix: Service Discovery + DNS + Load Balancers

| Layer | Role |
|---|---|
| **Service Discovery** | A registry (Consul, etcd, Kubernetes Service/Endpoints, Eureka) tracks which instances are currently healthy and where |
| **DNS** | Clients resolve a **name** (`payments.internal`) instead of an IP; DNS record maps to current healthy targets |
| **Load Balancer** | Distributes traffic across healthy instances, removes unhealthy ones via health checks (L4 e.g. NLB, or L7 e.g. ALB/Nginx) |

**Flow:** Client → DNS resolves `service-name` → Load Balancer VIP → LB checks its registered healthy backends (from service discovery / health checks) → routes to an actual instance.

**Kubernetes-specific talking point (very likely to come up):** a K8s `Service` gives you a stable virtual IP/DNS name; `Endpoints`/`EndpointSlices` are updated automatically as pods come and go, and `kube-proxy` or the service mesh handles routing — so app code never touches pod IPs directly.

---

# PART 2 — OPERATING SYSTEMS

## 1. Processes vs Threads

| | Process | Thread |
|---|---|---|
| Definition | Independent program in execution, own memory space | Unit of execution within a process, shares memory with sibling threads |
| Memory | Isolated (own address space) | Shared (code, data, heap); own stack + registers |
| Creation cost | Expensive (fork/exec) | Cheap (lighter weight) |
| Communication | IPC needed (pipes, sockets, shared memory) | Direct via shared memory (needs synchronization) |
| Crash impact | One process crashing doesn't kill another | One thread crashing can take down the whole process |

### PCB (Process Control Block)
Kernel data structure per process, holding everything needed to suspend and resume it:
- Process ID (PID), process state (running/waiting/ready)
- Program counter, CPU registers
- Memory management info (page tables)
- Open file descriptors
- Scheduling info (priority, etc.)

### Context Switching
Saving the CPU state (registers, PC) of the currently running process/thread into its PCB, and loading the state of the next one to run.
- Pure overhead — no useful work happens during a context switch.
- Triggered by: timer interrupt (time slice expired), I/O wait, higher-priority process arrival, syscall/interrupt.
- Thread context switches (within same process) are cheaper than process context switches (no address space / page table swap needed).

---

## 2. Threads — Concurrency vs Parallelism

- **Concurrency**: dealing with multiple tasks by interleaving execution (can be on a single core — tasks *appear* to progress together).
- **Parallelism**: multiple tasks literally executing at the same instant (requires multiple cores).
- **Multithreading**: multiple threads within one process, can be concurrent (single core, time-sliced) or parallel (multi-core).

**One-liner for interview:** "Concurrency is about structure — dealing with lots of things at once. Parallelism is about execution — doing lots of things at once."

---

## 3. CPU Scheduling Algorithms

| Algorithm | Idea | Pros | Cons |
|---|---|---|---|
| **FCFS** (First Come First Served) | Run in arrival order | Simple, fair in order | **Convoy effect** — short jobs stuck behind long ones |
| **SJF** (Shortest Job First) | Run shortest burst time next | Minimizes avg waiting time (optimal for that metric) | Needs to know burst time in advance; can **starve** long jobs |
| **Round Robin** | Fixed time quantum per process, cycle through ready queue | Fair, good for time-sharing/interactive systems | Too small a quantum → high context-switch overhead; too large → degenerates to FCFS |
| **Priority Scheduling** | Highest priority runs first | Good for real-time / important tasks | Can **starve** low-priority processes → fixed with **aging** (gradually increase waiting process's priority) |

### Quick worked example (SJF, non-preemptive)
```
Process   Arrival   Burst
P1        0         6
P2        1         2
P3        2         8
P4        3         3

Order run: P1(0-6) -> P2(6-8) -> P4(8-11) -> P3(11-19)
(P2 arrives at 1 but P1 already started & non-preemptive, so P1 runs to completion first)
```
Know how to compute **waiting time** and **turnaround time** — a common follow-up.
- Turnaround time = Completion time − Arrival time
- Waiting time = Turnaround time − Burst time

---

## 4. Deadlocks ⭐ (High-frequency topic)

### The 4 Necessary Conditions (Coffman conditions) — ALL must hold for deadlock:
1. **Mutual Exclusion** — resource held by only one process at a time
2. **Hold and Wait** — process holds a resource while waiting for another
3. **No Preemption** — resource can't be forcibly taken away
4. **Circular Wait** — a cycle of processes, each waiting on the next

### Classic deadlock example (two locks, wrong order)
```python
# Thread A
lock1.acquire()
lock2.acquire()   # waits for lock2, held by Thread B
...

# Thread B
lock2.acquire()
lock1.acquire()   # waits for lock1, held by Thread A
...
# --> DEADLOCK: circular wait
```
**Fix:** always acquire locks in a **consistent global order** (e.g., always lock1 before lock2, regardless of thread).

### Handling Strategies

| Approach | How | Example |
|---|---|---|
| **Prevention** | Break one of the 4 conditions structurally | Lock ordering (breaks circular wait); acquire all resources upfront (breaks hold-and-wait); allow preemption |
| **Avoidance** | Grant resources only if the resulting state is "safe" | **Banker's Algorithm** — checks if allocation keeps system in a state where all processes can still finish |
| **Detection** | Let deadlocks happen, periodically check via **resource allocation graph** for cycles, then recover | Kill/rollback a process, or preempt a resource |
| **Recovery** | Once detected | Process termination (kill one or all in the cycle), or resource preemption (roll back a process's held resources) |

**Banker's Algorithm — one-line description for interview:** Before granting a resource request, simulate the allocation; if there still exists *some* order in which all processes can obtain their max needed resources and finish, the state is "safe" and the request is granted — otherwise it's denied/deferred.

---

## 5. Memory Management

| Concept | Definition |
|---|---|
| **Paging** | Physical memory divided into fixed-size **frames**; process's logical memory divided into same-size **pages**; a **page table** maps pages → frames. Eliminates external fragmentation (but has internal fragmentation on the last page). |
| **Segmentation** | Memory divided into variable-size logical **segments** (code, stack, heap, data) matching the program's structure. More intuitive but causes external fragmentation. |
| **Virtual Memory** | Each process gets its own virtual address space, mapped to physical memory (or disk) via page tables — gives illusion of more memory than physically exists, and isolates processes from each other. |
| **Page Fault** | CPU references a page **not currently in physical memory** → trap to OS → OS loads the page from disk (swap/page file) into a free frame → resumes instruction. Expensive (disk I/O involved). |
| **TLB** (Translation Lookaside Buffer) | Small, fast hardware cache of recent virtual→physical address translations, inside the CPU/MMU. Avoids walking the full page table (which can be multiple memory accesses) on every memory reference. **TLB miss** → fall back to page table walk. |

**Interview follow-up to expect:** "What happens on a TLB miss vs a page fault?"
- TLB miss: translation not cached → CPU/MMU walks the page table in memory → if found, loads into TLB, continues. Slower than a hit, but not catastrophic.
- Page fault: the page isn't in physical memory *at all* → OS must go to disk → orders of magnitude slower, may trigger a context switch to let another process run while I/O happens.

---

## 6. Synchronization

| Concept | Definition |
|---|---|
| **Critical Section** | The part of code that accesses shared resources and must not be executed by more than one thread/process at a time |
| **Race Condition** | Bug that occurs when the outcome depends on timing/interleaving of concurrent access to shared data |
| **Mutex** | Locking primitive: binary, **ownership-based** — only the thread that locked it can unlock it. Enforces mutual exclusion for a critical section. |
| **Semaphore** | Counter-based signaling primitive, **not ownership-based** — any thread can signal/wait. `wait()`/`P()` decrements, `signal()`/`V()` increments; blocks when count is 0. Binary semaphore (count 1) can look like a mutex but semantically is for **signaling**, not exclusive ownership. |

### Race condition example (classic: `count++` isn't atomic)
```python
# count++ is actually: LOAD count, ADD 1, STORE count — 3 steps, not atomic
import threading

counter = 0
def increment():
    global counter
    for _ in range(100000):
        counter += 1   # RACE CONDITION if run from multiple threads

threads = [threading.Thread(target=increment) for _ in range(2)]
[t.start() for t in threads]
[t.join() for t in threads]
print(counter)  # likely NOT 200000, due to lost updates
```

### Fixed with a mutex
```python
lock = threading.Lock()

def increment():
    global counter
    for _ in range(100000):
        with lock:          # critical section
            counter += 1
```

### Producer-Consumer with Semaphores (the canonical synchronization interview question)
```python
import threading

BUFFER_SIZE = 5
buffer = []

empty = threading.Semaphore(BUFFER_SIZE)  # counts free slots
full = threading.Semaphore(0)             # counts filled slots
mutex = threading.Lock()                  # protects buffer itself

def producer(item):
    empty.acquire()      # wait for a free slot
    with mutex:
        buffer.append(item)
    full.release()        # signal: one more item available

def consumer():
    full.acquire()        # wait for an available item
    with mutex:
        item = buffer.pop(0)
    empty.release()        # signal: one more free slot
    return item
```
**Why this matters:** `empty`/`full` semaphores handle the *waiting* (blocking producer when buffer is full, blocking consumer when empty) — a mutex alone can't do that, it can only protect the critical section, not block/wake threads based on a count. This distinction (mutex = exclusion, semaphore = signaling/counting) is a favorite "explain the difference" question.

---

# Quick-Fire Answers (for rapid-fire rounds)

- **Why timeout before retry?** Without a timeout you don't know when to give up and retry — you'd hang forever.
- **Why jitter in backoff?** Prevents synchronized retry storms (thundering herd) from many clients recovering at the same instant.
- **Why idempotency matters for retries/queues?** At-least-once delivery + retries mean the same operation can run twice; without idempotency you get duplicate side effects (double charges, duplicate rows).
- **Mutex vs Semaphore, one line:** Mutex = ownership-based lock for exclusion; semaphore = counter for signaling/resource counting, no ownership.
- **Paging vs segmentation, one line:** Paging = fixed-size chunks, no external fragmentation; segmentation = logical variable-size chunks, external fragmentation possible.
- **Why not hard-code IPs?** IPs are ephemeral in cloud/container environments (autoscaling, restarts, deployments) — hard-coding breaks the moment topology changes and prevents load balancing/failover.
