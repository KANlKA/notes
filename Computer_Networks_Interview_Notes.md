# Computer Networks — Interview Prep Notes

---

## 1. OSI Model (7 Layers)

Mnemonic: **"Please Do Not Throw Sausage Pizza Away"** (Physical → Application)

| Layer | Name | Function | Examples/Protocols | PDU |
|---|---|---|---|---|
| 7 | Application | User-facing services, network access to apps | HTTP, FTP, SMTP, DNS | Data |
| 6 | Presentation | Data formatting, encryption, compression | SSL/TLS, JPEG, ASCII | Data |
| 5 | Session | Establishes/manages/terminates sessions | NetBIOS, RPC, sockets | Data |
| 4 | Transport | End-to-end delivery, reliability, flow control | TCP, UDP | Segment (TCP) / Datagram (UDP) |
| 3 | Network | Logical addressing, routing between networks | IP, ICMP, routers | Packet |
| 2 | Data Link | Physical addressing (MAC), error detection on a link | Ethernet, switches, ARP | Frame |
| 1 | Physical | Raw bit transmission over medium | Cables, hubs, radio waves | Bits |

**Interview one-liner:** "OSI is a conceptual 7-layer reference model that standardizes how different network functions interact. In practice, most systems follow the simpler 4-layer TCP/IP model."

---

## 2. TCP/IP Model (4 Layers)

| Layer | Maps to OSI | Protocols |
|---|---|---|
| Application | App + Presentation + Session | HTTP, HTTPS, FTP, DNS, SMTP |
| Transport | Transport | TCP, UDP |
| Internet | Network | IP, ICMP, ARP |
| Network Access (Link) | Data Link + Physical | Ethernet, Wi-Fi |

**Why TCP/IP over OSI in practice?** It's what the actual internet was built on; OSI is more of a teaching/reference model.

---

## 3. TCP (Transmission Control Protocol)

### 3.1 Three-Way Handshake (Connection Establishment)

```
Client                          Server
  | ---------- SYN (seq=x) --------> |
  | <----- SYN-ACK (seq=y, ack=x+1)--|
  | ---------- ACK (ack=y+1) ------> |
  |         [Connection established]  |
```

1. **SYN**: Client sends a segment with SYN flag set, initial sequence number `x`.
2. **SYN-ACK**: Server responds with SYN+ACK, its own sequence number `y`, and acknowledges `x+1`.
3. **ACK**: Client acknowledges `y+1`. Connection is now established (full-duplex).

**Why 3-way and not 2-way?** Both sides need to synchronize sequence numbers in *both* directions since TCP is full-duplex. A 2-way handshake can't confirm the server's sequence number was received by the client.

### 3.2 Connection Termination (4-way)

```
Client                          Server
  | --------- FIN ------------------>|
  | <-------- ACK --------------------|
  | <-------- FIN ---------------------|
  | --------- ACK ------------------->|
```
Each side closes its own direction independently → hence 4 steps (can be optimized to 3 if ACK+FIN combined).

### 3.3 Reliability

- **Sequence numbers**: every byte is numbered; receiver can detect missing/out-of-order data.
- **Acknowledgments (ACKs)**: receiver confirms bytes received.
- **Retransmission**: if ACK not received before timeout (RTO), sender retransmits.
- **Checksums**: detect corrupted data.
- **Ordered delivery**: receiver buffers and reorders out-of-order segments.

### 3.4 Flow Control

- Prevents a fast sender from overwhelming a slow receiver.
- Achieved via **sliding window**: receiver advertises a `window size` (rwnd) in each ACK indicating how much buffer space it has left.
- Sender can send at most `rwnd` unacknowledged bytes.

### 3.5 Congestion Control

Prevents the sender from overwhelming the **network** (not just receiver).

- **Slow Start**: cwnd starts small (e.g., 1 MSS), doubles every RTT until it hits `ssthresh` or packet loss.
- **Congestion Avoidance**: after ssthresh, cwnd grows linearly (+1 MSS per RTT) — AIMD (Additive Increase).
- **Multiplicative Decrease**: on packet loss, cwnd is cut (e.g., halved for CUBIC/Reno, or reset to 1 on timeout).
- **Fast Retransmit**: 3 duplicate ACKs → retransmit immediately without waiting for timeout.
- **Fast Recovery**: avoid dropping cwnd all the way to 1 after fast retransmit.

**Actual send rate** = `min(cwnd, rwnd)`

**Interview tip:** Be ready to say "TCP achieves reliability through ACKs + retransmission, flow control via sliding window (protects receiver), and congestion control via AIMD (protects network)."

---

## 4. UDP (User Datagram Protocol)

### Characteristics
- Connectionless — no handshake.
- Unreliable — no ACKs, no retransmission, no ordering guarantee.
- No flow/congestion control (application must handle if needed).
- Lightweight header (8 bytes: src port, dst port, length, checksum) vs TCP's 20+ bytes.
- Faster, lower latency, lower overhead.

### Use Cases
- DNS queries (single request/response, latency-sensitive)
- Video/audio streaming, VoIP (occasional dropped packet is fine, retransmission would cause lag)
- Online gaming (real-time, stale data is worse than missing data)
- DHCP, SNMP
- QUIC/HTTP3 is built on UDP (reliability implemented at app layer for more flexibility)

### TCP vs UDP Table

| Feature | TCP | UDP |
|---|---|---|
| Connection | Connection-oriented | Connectionless |
| Reliability | Reliable (ACK, retransmit) | Unreliable |
| Ordering | Guaranteed | Not guaranteed |
| Speed | Slower (overhead) | Faster |
| Header size | 20-60 bytes | 8 bytes |
| Flow/congestion control | Yes | No |
| Use case | Web, email, file transfer | Streaming, gaming, DNS |

---

## 5. HTTP Methods

| Method | Purpose | Idempotent? | Safe? | Body? |
|---|---|---|---|---|
| GET | Retrieve a resource | Yes | Yes | No (conventionally) |
| POST | Create a resource / submit data | No | No | Yes |
| PUT | Replace a resource entirely | Yes | No | Yes |
| PATCH | Partially update a resource | Not guaranteed (usually treated as not idempotent) | No | Yes |
| DELETE | Remove a resource | Yes | No | Optional |

- **Idempotent** = calling it N times has the same effect as calling it once (GET, PUT, DELETE).
- **Safe** = doesn't modify server state (GET only, technically HEAD/OPTIONS too).

**PUT vs PATCH interview answer:** "PUT replaces the whole resource — if you omit a field, it may get nulled out. PATCH applies a partial update — only the fields you send are changed."

---

## 6. HTTP Status Codes

| Code | Meaning | Notes |
|---|---|---|
| **200** OK | Success | Standard success response |
| **201** Created | Resource created | Typically after POST/PUT; often includes `Location` header |
| **301** Moved Permanently | Permanent redirect | Browsers/search engines update bookmarks/links |
| **400** Bad Request | Client sent malformed/invalid request | e.g., invalid JSON, missing required field |
| **401** Unauthorized | Not authenticated | Missing/invalid credentials |
| **403** Forbidden | Authenticated but not authorized | Valid identity, insufficient permission |
| **404** Not Found | Resource doesn't exist | |
| **500** Internal Server Error | Generic server-side failure | Unhandled exception |
| **502** Bad Gateway | Upstream server sent invalid response | Gateway/proxy issue between servers |
| **503** Service Unavailable | Server temporarily overloaded/down | Often used with `Retry-After` header |

**401 vs 403 (classic interview question):** "401 means 'I don't know who you are' (authentication failure). 403 means 'I know who you are, but you're not allowed' (authorization failure)."

**502 vs 503:** "502 means the proxy/gateway got an invalid response from the upstream server. 503 means the server itself is overloaded or down for maintenance."

---

## 7. DNS ⭐ (High Priority)

### Key Components

- **Domain**: Human-readable name (e.g., `google.com`).
- **Resolver**: Usually your ISP or a public resolver (e.g., Google 8.8.8.8, Cloudflare 1.1.1.1) — does the actual lookup work on behalf of the client, recursively querying other servers.
- **Root server**: Top of the DNS hierarchy (13 logical root server clusters worldwide); knows where TLD servers are.
- **TLD server**: Handles top-level domains like `.com`, `.org`, `.in`; knows which authoritative server handles a specific domain.
- **Authoritative server**: Holds the actual DNS records (A, AAAA, CNAME, MX, etc.) for a domain; gives the final answer.
- **DNS Caching**: Happens at multiple levels — browser cache, OS cache, resolver cache — each record has a **TTL (Time To Live)** after which it must be re-fetched. Reduces latency and load on authoritative servers.

### Common DNS Record Types
| Record | Purpose |
|---|---|
| A | Maps domain → IPv4 address |
| AAAA | Maps domain → IPv6 address |
| CNAME | Alias — maps domain → another domain |
| MX | Mail server for the domain |
| NS | Nameserver for the domain |
| TXT | Arbitrary text (SPF, verification, etc.) |

### What happens when you type `google.com`? (Classic Interview Question)

1. **Browser cache check**: Browser checks if it already has a cached DNS entry.
2. **OS cache check**: If not in browser, OS checks its own DNS cache (and `hosts` file).
3. **Recursive resolver query**: If not cached, request goes to a configured DNS resolver (e.g., ISP's or 8.8.8.8).
4. **Root server**: Resolver asks a root server → root replies "ask the `.com` TLD server."
5. **TLD server**: Resolver asks `.com` TLD server → it replies "ask `google.com`'s authoritative nameserver."
6. **Authoritative server**: Resolver asks authoritative server → gets back the A/AAAA record (IP address).
7. **Resolver caches and returns** the IP to the browser (respecting TTL).
8. **TCP connection**: Browser initiates a TCP 3-way handshake with that IP (port 443 for HTTPS).
9. **TLS handshake**: Browser and server negotiate encryption (certificate exchange, key exchange).
10. **HTTP request**: Browser sends an HTTP GET request over the encrypted channel.
11. **Server responds** with HTML; browser parses it, discovers more resources (CSS, JS, images), and repeats the process (often reusing the same connection / new parallel connections) for each.
12. **Rendering**: Browser renders the page (DOM construction, CSSOM, render tree, layout, paint).

**Good structured answer format for interview**: DNS resolution → TCP handshake → TLS handshake → HTTP request/response → Rendering.

---

## 8. HTTPS

### TLS (Transport Layer Security)
- Sits between Transport (TCP) and Application (HTTP) layers — provides encryption, integrity, and authentication.
- Successor to SSL (SSL is deprecated/insecure; people still say "SSL certificate" colloquially but it's really TLS).

### TLS Handshake (simplified, TLS 1.2-style)
1. **ClientHello**: Client sends supported TLS versions, cipher suites, a random number.
2. **ServerHello**: Server picks cipher suite, sends its random number and its **certificate** (contains public key).
3. **Certificate validation**: Client verifies the certificate against trusted Certificate Authorities (CAs).
4. **Key exchange**: Client (and server, via Diffie-Hellman in modern TLS) generate a shared **session key** using asymmetric crypto.
5. **Finished**: Both sides switch to symmetric encryption using the derived session key for the rest of the communication.

(TLS 1.3 reduces this to a 1-RTT handshake, faster than TLS 1.2's 2-RTT.)

### Certificates
- Issued by a **Certificate Authority (CA)** — trusted third party.
- Contains: domain name, public key, issuer, validity period, digital signature.
- Browser has a list of trusted root CAs; verifies the chain of trust (Root CA → Intermediate CA → Leaf/domain cert).

### Encryption Basics
- **Symmetric encryption**: same key encrypts and decrypts (fast) — e.g., AES. Used for the actual data transfer after handshake.
- **Asymmetric encryption**: public/private key pair (slow, but no shared-secret problem) — e.g., RSA, ECC. Used during handshake to safely exchange/derive the symmetric key.
- **Why hybrid?** Asymmetric is computationally expensive; TLS uses it just once to establish a shared symmetric key, then uses fast symmetric encryption for bulk data.

**One-liner:** "HTTPS = HTTP + TLS. TLS uses asymmetric crypto to securely establish a symmetric session key, then encrypts all traffic with that symmetric key for speed."

---

## 9. Other Core Concepts

### IP (Internet Protocol)
- Provides logical addressing and routing (best-effort, connectionless — unlike TCP, no guarantee of delivery).

### IPv4 vs IPv6
| | IPv4 | IPv6 |
|---|---|---|
| Address size | 32-bit | 128-bit |
| Format | Dotted decimal (192.168.1.1) | Hex, colon-separated (2001:0db8::1) |
| Address space | ~4.3 billion | ~340 undecillion |
| NAT needed? | Often (address exhaustion) | Not needed, huge space |
| Header | More complex, checksum present | Simplified, no checksum (leaves it to upper layers) |

### MAC Address
- 48-bit physical address burned into the network interface card (NIC).
- Used for communication within the same local network segment (Layer 2).
- Format: `AA:BB:CC:DD:EE:FF`.

### ARP (Address Resolution Protocol)
- Maps an IP address → MAC address within a local network.
- Process: Host broadcasts "Who has IP x.x.x.x?" → owner replies with its MAC address → requester caches it (ARP cache/table).

### DHCP (Dynamic Host Configuration Protocol)
- Automatically assigns IP addresses (and subnet mask, gateway, DNS servers) to devices on a network.
- **DORA process**:
  1. **Discover**: Client broadcasts to find a DHCP server.
  2. **Offer**: Server offers an available IP.
  3. **Request**: Client requests that specific IP.
  4. **Acknowledge**: Server confirms the lease.

### NAT (Network Address Translation)
- Translates private IP addresses (e.g., `192.168.x.x`) to a public IP for internet access, and vice versa for responses.
- Solves IPv4 exhaustion; also adds a layer of security (internal IPs hidden from outside).
- Router maintains a translation table (private IP:port ↔ public IP:port).

### Cookies
- Small key-value data stored in the browser, sent with every HTTP request to the same domain.
- Used for: session management, personalization, tracking.
- Attributes: `Expires`/`Max-Age`, `Secure` (HTTPS only), `HttpOnly` (not accessible via JS, mitigates XSS), `SameSite` (CSRF protection).

### Sessions
- Server-side mechanism to maintain state about a user across multiple requests (since HTTP is stateless).
- Typically implemented via a **session ID** stored in a cookie; actual session data lives on the server (or in a distributed store like Redis).
- **Cookies vs Sessions**: Cookie is the *transport mechanism* (stored client-side); Session is the *server-side state* referenced by the cookie's session ID.

---

## 10. Quick-Fire Interview Q&A

**Q: Why does TCP need both flow control and congestion control?**
A: Flow control protects the *receiver* from being overwhelmed (based on receiver's buffer). Congestion control protects the *network* from being overwhelmed (based on network conditions/packet loss).

**Q: Why is UDP used for DNS instead of TCP?**
A: DNS queries are small, single request/response — the overhead of a TCP handshake isn't worth it. (Note: DNS *can* fall back to TCP for large responses, e.g., zone transfers or responses >512 bytes.)

**Q: What's the difference between a forward proxy and a reverse proxy?**
A: Forward proxy sits in front of clients, hiding client identity from the server (e.g., corporate proxy). Reverse proxy sits in front of servers, hiding server identity from clients (e.g., load balancer, Nginx, CDN edge).

**Q: What is a port, and why do we need it?**
A: A 16-bit number identifying a specific process/service on a host, allowing multiple applications to share one IP address (e.g., 80 = HTTP, 443 = HTTPS, 22 = SSH).

**Q: What causes a 504 vs 502?**
A: 502 = upstream sent an invalid/garbled response. 504 = upstream didn't respond in time (Gateway Timeout).

**Q: How does HTTPS prevent man-in-the-middle attacks?**
A: Certificate validation (chain of trust to a CA) ensures you're talking to the real server, and encryption ensures an intercepted packet is unreadable/unmodifiable without detection.

---

## 11. Simple Code Reference (if asked to demonstrate)

### Basic TCP socket client/server in Python (conceptual demo)

```python
# server.py
import socket

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)  # TCP
server.bind(('0.0.0.0', 8080))
server.listen(5)
print("Listening on port 8080...")

while True:
    conn, addr = server.accept()   # blocks until 3-way handshake completes
    print(f"Connected by {addr}")
    data = conn.recv(1024)
    conn.sendall(b"HTTP/1.1 200 OK\r\n\r\nHello, World!")
    conn.close()
```

```python
# client.py
import socket

client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect(('127.0.0.1', 8080))  # triggers 3-way handshake
client.sendall(b"GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")
print(client.recv(4096).decode())
client.close()
```

### UDP socket example

```python
# udp_server.py
import socket
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)  # UDP
sock.bind(('0.0.0.0', 9090))
data, addr = sock.recvfrom(1024)
print(f"Received: {data} from {addr}")
sock.sendto(b"ACK", addr)
```

```python
# udp_client.py
import socket
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.sendto(b"Hello UDP", ('127.0.0.1', 9090))
print(sock.recvfrom(1024))
```

### Simple `curl` commands to demonstrate HTTP methods

```bash
curl -X GET https://api.example.com/users/1
curl -X POST https://api.example.com/users -d '{"name":"Ravi"}' -H "Content-Type: application/json"
curl -X PUT https://api.example.com/users/1 -d '{"name":"Ravi","age":30}' -H "Content-Type: application/json"
curl -X PATCH https://api.example.com/users/1 -d '{"age":31}' -H "Content-Type: application/json"
curl -X DELETE https://api.example.com/users/1
```

### DNS lookup from terminal

```bash
dig google.com          # detailed DNS query
nslookup google.com     # simpler DNS query
dig +trace google.com   # shows full resolution path: root -> TLD -> authoritative
```

---

## 12. Coupa-Specific Prep Notes

Coupa is a **B2B SaaS (procurement/spend management)** company — expect networking questions framed around:
- **API design/integration** (REST, HTTP status codes, idempotency for retries in procurement workflows — e.g., "why must a PUT to create a purchase order be idempotent?")
- **Security** (HTTPS, TLS, cookies/sessions, auth — since they handle sensitive financial/vendor data)
- **System reliability** (TCP reliability, retries, timeouts, 502/503 handling for microservices communicating with each other)
- **Multi-tenant SaaS concerns**: DNS/subdomains per customer, load balancing, caching (CDN + DNS TTL)

Be ready to connect networking fundamentals to **real-world API/system design scenarios**, not just textbook definitions — SaaS companies like Coupa often ask "how would you design a resilient integration between two services" rather than pure trivia.

---

### Final Tip
When answering, structure responses as: **Definition → How it works → Why it matters / real-world example**. This shows depth beyond memorization.
