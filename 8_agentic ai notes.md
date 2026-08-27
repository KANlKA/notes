# Interview Prep Notes: AI/Agent Projects + Time & Space Complexity
### (Target: Coupa, L3-style rounds)

---

# PART 1 — AI / AGENT PROJECT QUESTIONS

**Mindset for this section:** if it's on your resume, assume they'll pick one bullet point and go 4 levels deep on it ("why", "what if it failed", "how did you measure it"). Every subsection below ends with the *"gotcha" follow-up* they're likely to ask.

---

## 1. LLM Fundamentals

| Concept | Definition | Interview gotcha |
|---|---|---|
| **Tokens** | Sub-word units the model actually processes (via BPE/tokenizer), not words. ~4 chars ≈ 1 token in English. | "Why does token count matter for cost/latency?" → billing is per-token, and generation is sequential (one token at a time), so more output tokens = more latency, linearly. |
| **Context window** | Max tokens (input + output combined) the model can attend to in one call. | "What happens if you exceed it?" → request is truncated or rejected; for long documents you need chunking/RAG rather than stuffing everything in. |
| **Temperature** | Controls randomness in sampling from the output probability distribution. 0 = near-deterministic/greedy, higher = more diverse/creative. | "Why use temp=0 in production?" → reproducibility for things like classification/extraction; higher temp for creative/brainstorming tasks. Also mention **top_p** (nucleus sampling) as the sibling knob. |
| **Hallucinations** | Model generates fluent but factually wrong or unsupported content, because it's predicting plausible tokens, not looking up truth. | "How did you mitigate this?" → RAG (ground in real documents), structured output + validation, lower temperature, citations/source-attribution, asking the model to say "I don't know" explicitly in the prompt, human-in-the-loop for high-stakes outputs. |
| **Prompting** | Techniques: zero-shot, few-shot (examples in-prompt), chain-of-thought (ask model to reason step by step), system prompt for role/constraints. | "How do you prevent prompt injection?" → treat any user-supplied/retrieved content as untrusted data, not instructions; separate system instructions from user content; validate/sanitize outputs. |
| **Structured output** | Forcing the model to return a specific schema — JSON mode, function-calling schemas, or grammar-constrained decoding — instead of free text. | "Why not just parse free text?" → free text parsing is brittle (model phrasing varies); structured output lets you validate against a schema and fail fast/retry on malformed output. |

### Structured output example (schema-constrained JSON)
```python
response = client.messages.create(
    model="claude-...",
    max_tokens=500,
    system="You must respond ONLY with valid JSON matching this schema: "
           '{"name": string, "amount": number, "category": string}. '
           "No preamble, no markdown fences.",
    messages=[{"role": "user", "content": "Extract from: 'Bought 3 chairs for $450, office supplies'"}]
)
import json
try:
    data = json.loads(response.content[0].text)
except json.JSONDecodeError:
    # retry once with a stricter reminder, or fall back to a repair step
    ...
```

---

## 2. RAG (Retrieval-Augmented Generation)

**The core idea to be able to say in one breath:** instead of relying on the model's frozen training knowledge, you retrieve relevant chunks of your own data at query time and inject them into the prompt, so the model answers grounded in current, specific, private information.

| Concept | Definition | Interview gotcha |
|---|---|---|
| **Embeddings** | A vector (list of floats) representing the semantic meaning of a piece of text, produced by an embedding model. Similar meaning → vectors close together (cosine similarity / dot product). | "Why not keyword search?" → embeddings capture semantic similarity ("car" ≈ "automobile") that exact keyword match misses. Good answer also mentions **hybrid search** (BM25 + embeddings) as the pragmatic real-world choice. |
| **Vector database** | Stores embeddings + metadata, supports fast approximate nearest-neighbor (ANN) search over millions of vectors. (Pinecone, Weaviate, pgvector, FAISS, Milvus) | "How does it scale to millions of vectors?" → ANN indexes like **HNSW** (graph-based) or **IVF** (clustering-based) trade a little recall for huge speed vs brute-force exact search. |
| **Chunking** | Splitting source documents into smaller pieces before embedding, since embedding models and the LLM context window both have size limits. | "How did you choose chunk size?" → trade-off: too small = loses context/coherence; too large = dilutes relevance and wastes context window. Common approach: few-hundred-token chunks with overlap (e.g., 500 tokens, 50-token overlap) to avoid cutting sentences/ideas at boundaries. Semantic chunking (split on section/paragraph boundaries) beats naive fixed-size splitting. |
| **Retrieval** | Given a query, embed it and find the top-k most similar chunks from the vector DB. | "What if top-k retrieval misses the right chunk?" → this is a real limitation; mitigations: better chunking, hybrid search, query rewriting/expansion, increasing k then reranking. |
| **Reranking** | A second, more expensive/accurate model re-scores the top-k retrieved candidates (cross-encoder, not just cosine similarity) to reorder them before feeding to the LLM. | "Why not just retrieve more and skip reranking?" → embeddings similarity (bi-encoder) is fast but approximate; a cross-encoder reranker directly compares query+doc together, more accurate but too slow to run over the whole corpus — so you use it only on the pre-filtered top-k. |
| **Context injection** | Formatting retrieved chunks into the prompt (with citations/source markers) before the actual LLM generation call. | "How do you handle conflicting sources?" → worth having an answer: instruct the model to note disagreement, prioritize by recency/source authority, or surface conflict to the user rather than silently picking one. |

### Minimal RAG pipeline (conceptual code)
```python
def rag_answer(query):
    query_vector = embed(query)
    candidates = vector_db.search(query_vector, top_k=20)     # retrieval
    reranked = reranker.score(query, candidates)[:5]            # reranking
    context = "\n\n".join(f"[{i}] {c.text}" for i, c in enumerate(reranked))

    prompt = f"""Answer using ONLY the context below. Cite sources as [n].
If the answer isn't in the context, say you don't know.

Context:
{context}

Question: {query}"""

    return llm.generate(prompt)
```

---

## 3. Agents

**Core idea:** an LLM that doesn't just generate text once, but runs in a loop — deciding which tool to call, observing the result, and deciding the next action — until the task is done.

| Concept | Definition | Interview gotcha |
|---|---|---|
| **Tool calling** | The model outputs a structured request to invoke an external function (with args), your code executes it, and feeds the result back to the model. | "How does the model know which tool to use?" → tool name + description + parameter schema are given to the model in the request; it's trained to pick based on that description — so **tool descriptions matter a lot**, same as good docstrings. |
| **Agent loop** | Think → Act → Observe → repeat, until the model decides it's done or a stop condition (max iterations, task complete) is hit. | "What stops an infinite loop?" → a hard max-iteration cap, cost/time budget, or explicit "done" signal from the model — always have a circuit breaker on the loop itself. |
| **State** | What the agent is currently tracking — conversation history, intermediate results, current step. | "How do you keep state manageable as tasks get long?" → summarization of old turns, or storing state externally (DB/memory) instead of shoving everything into context. |
| **Memory** | Persisting information across turns/sessions beyond just the immediate context window (short-term = conversation buffer, long-term = stored facts/embeddings retrieved when relevant). | "How is this different from context window?" → context window is what's *currently* fed to the model; memory is a retrieval system that selectively pulls relevant past info back into context when needed — essentially RAG applied to the agent's own history. |
| **Planning** | Breaking a complex goal into sub-steps before/while executing (task decomposition), sometimes via an explicit "plan" step before acting. | "How do you know the plan is right?" → often you don't upfront; **re-planning** after each observation (adjusting the plan based on real tool results) is more robust than committing to a rigid upfront plan. |
| **Tool failures** | A called tool errors, times out, or returns unexpected data. | "How does the agent recover?" → feed the error back to the model as an observation so it can retry differently, pick another tool, or ask the user for clarification — same retry/backoff/circuit-breaker thinking from distributed systems applies here too. |
| **Guardrails** | Constraints preventing the agent from doing something harmful/wrong/out-of-scope — input validation, output validation, allow-listing which tools/actions are permitted, human approval for high-risk actions (e.g., actually sending an email or making a payment). | "Give a concrete example." → e.g., an agent that can *draft* a purchase order but requires human approval before it actually submits one; or a tool allow-list so the agent can only call read-only APIs, never destructive ones, without explicit escalation. |

### Simplified agent loop (conceptual code)
```python
def run_agent(user_goal, max_steps=8):
    messages = [{"role": "user", "content": user_goal}]

    for step in range(max_steps):
        response = llm.generate(messages, tools=AVAILABLE_TOOLS)

        if response.stop_reason == "end_turn":
            return response.text   # agent decided it's done

        if response.stop_reason == "tool_use":
            tool_call = response.tool_call
            try:
                result = execute_tool(tool_call.name, tool_call.args)
            except ToolError as e:
                result = f"Tool failed: {e}"  # feed error back, let model adapt

            messages.append({"role": "assistant", "content": response.raw})
            messages.append({"role": "tool_result", "content": result})

    return "Max steps reached without completion"  # circuit breaker on the loop
```

---

## 4. Production Concerns

This section is where interviewers separate "built a demo" from "shipped something real." Have a real number or trade-off ready for each row if it's your project.

| Concern | What to know | Interview gotcha |
|---|---|---|
| **API costs** | Priced per input + output token, output tokens usually cost more; cost scales with context size × number of calls. | "How would you cut costs?" → shorter/cached prompts, cheaper model for simpler sub-tasks (model routing — small model for classification, big model for reasoning), caching repeated prefixes, reducing retrieved context to only what's needed. |
| **Latency** | LLM generation is sequential per-token, so output length dominates latency; network + retrieval add on top. | "How do you make an agent feel fast despite multi-step tool calls?" → streaming tokens to the UI as they're generated, parallelizing independent tool calls, showing intermediate progress ("searching...", "found 3 results...") instead of one long spinner. |
| **Rate limits** | Providers cap requests/tokens per minute; bursts get throttled (429s). | "How do you handle a 429?" → same backoff+jitter pattern from distributed systems notes, plus request queuing/batching, and pre-emptive rate limiting client-side so you rarely hit the wall. |
| **Caching** | Cache LLM responses for repeated/identical queries; some providers offer **prompt caching** (reuse of a static prefix like a long system prompt across calls, cheaper + faster). | "What can't you cache?" → anything non-deterministic by design or genuinely unique per user query; also cache invalidation matters if underlying retrieved data changes (ties back to cache invalidation strategies — TTL / explicit / event-driven). |
| **Evaluation** | Need a way to measure quality beyond "looks right to me" — golden test sets with expected outputs, automated scoring (exact match, embedding similarity, or LLM-as-judge), human review sampling. | "How do you eval something subjective, like tone?" → LLM-as-judge with a rubric, or human eval on a sample, tracked over time as a regression suite whenever you change prompts/models. |
| **Observability** | Logging prompts, responses, tool calls, latencies, token counts, and errors per request — so you can debug a bad output after the fact and track drift/cost/latency trends. | "What would you log for a RAG pipeline specifically?" → the query, which chunks were retrieved (and their scores), the final prompt sent, the response, and user feedback if available — lets you tell whether a bad answer was a retrieval failure or a generation failure. |
| **Security** | Prompt injection (malicious instructions hidden in retrieved/user content), data leakage (don't let the model echo secrets/PII from context), tool-call permission boundaries. | "How do you defend against prompt injection from a retrieved document?" → treat retrieved content strictly as data not instructions in the system prompt, sanitize/strip suspicious instructions, restrict what tools the model can invoke without human confirmation, and never let retrieved text alone authorize an action. |

---

# PART 2 — TIME & SPACE COMPLEXITY

**Rule for every coding round, no exceptions:** after you finish a problem, say out loud — *"Time complexity is O(...), space is O(...), and here's how I could optimize it further if needed."* Interviewers notice when you skip this.

## Complexity Cheat Sheet

| Notation | Name | Typical example |
|---|---|---|
| O(1) | Constant | Array index access, hash map get/put (average) |
| O(log n) | Logarithmic | Binary search, balanced BST operations, heap push/pop |
| O(n) | Linear | Single pass through an array/list |
| O(n log n) | Linearithmic | Efficient sorting (merge sort, heap sort, quicksort avg) |
| O(n²) | Quadratic | Nested loops, naive sorting (bubble/insertion/selection) |
| O(2ⁿ) | Exponential | Naive recursive Fibonacci, subset/powerset generation, brute-force combinations |

---

## Per-Data-Structure Breakdown

### Arrays
| Operation | Time | Notes |
|---|---|---|
| Access by index | O(1) | — |
| Search (unsorted) | O(n) | — |
| Search (sorted, binary search) | O(log n) | — |
| Insert/delete at end | O(1) amortized | — |
| Insert/delete at start/middle | O(n) | shifting required |

### HashMap (Hash Table)
| Operation | Time (average) | Time (worst case) |
|---|---|---|
| Get / Put / Delete | O(1) | O(n) (all keys collide into one bucket) |
| Iteration | O(n) | — |

*Gotcha to mention:* worst case O(n) happens with a bad hash function causing many collisions; a good hash function + resizing keeps it O(1) average in practice.

### Trees (general, e.g. binary tree, not necessarily balanced)
| Operation | Time |
|---|---|
| Search / Insert / Delete | O(h) where h = height. Balanced: O(log n). Skewed (degenerates to a linked list): O(n) |
| Traversal (in/pre/post-order) | O(n) — visits every node |

### BST (Binary Search Tree)
| Operation | Balanced BST (e.g. AVL, Red-Black) | Unbalanced BST (worst case) |
|---|---|---|
| Search / Insert / Delete | O(log n) | O(n) |

*Gotcha:* a plain BST built by inserting sorted data in order degenerates into a linked list → O(n). This is exactly why self-balancing trees (AVL, Red-Black) exist.

### Sorting Algorithms
| Algorithm | Best | Average | Worst | Space |
|---|---|---|---|---|
| Bubble/Insertion/Selection Sort | O(n) / O(n) / O(n²) | O(n²) | O(n²) | O(1) |
| Merge Sort | O(n log n) | O(n log n) | O(n log n) | O(n) |
| Quick Sort | O(n log n) | O(n log n) | O(n²) (bad pivot choice) | O(log n) (recursion stack) |
| Heap Sort | O(n log n) | O(n log n) | O(n log n) | O(1) |

*Gotcha:* Quicksort's worst case (already-sorted input + naive pivot = first/last element) is O(n²) — mitigated with random pivot selection or median-of-three.

### Graphs
| Operation | Time | Notes |
|---|---|---|
| BFS / DFS | O(V + E) | V = vertices, E = edges |
| Dijkstra's (with min-heap) | O((V + E) log V) | Shortest path, non-negative weights |
| Adjacency matrix space | O(V²) | Good for dense graphs |
| Adjacency list space | O(V + E) | Good for sparse graphs (most real-world graphs) |

### Heap (Binary Heap / Priority Queue)
| Operation | Time |
|---|---|
| Peek (min/max) | O(1) |
| Insert (push) | O(log n) |
| Extract min/max (pop) | O(log n) |
| Build heap from array | O(n) (not O(n log n) — a classic surprising interview fact, worth mentioning) |

### Stack / Queue
| Operation | Time |
|---|---|
| Push / Pop (stack) | O(1) |
| Enqueue / Dequeue (queue, with proper implementation e.g. deque or circular buffer) | O(1) |

*Gotcha:* a queue implemented naively on top of a plain array (dequeue from front = shift everything) is O(n) per dequeue — use a circular buffer, linked list, or two-stack trick to get O(1).

### Linked List
| Operation | Singly Linked | Doubly Linked |
|---|---|---|
| Access by index | O(n) | O(n) |
| Insert/delete at head | O(1) | O(1) |
| Insert/delete at tail | O(n) (O(1) if tail pointer kept) | O(1) (with tail pointer) |
| Search | O(n) | O(n) |

---

## The Post-Problem Checklist (say this every time)

1. **Time:** What's the Big-O of my solution, and why? (identify the dominant loop/recursion)
2. **Space:** What extra memory am I using — auxiliary data structures, recursion call stack?
3. **Can I optimize?**
   - Am I doing repeated work I could **cache/memoize**?
   - Could a **hash map** replace a nested loop / linear search (trade space for time)?
   - Could **two pointers** or a **sliding window** avoid an O(n²) nested loop?
   - Is my recursion **exponential** because of overlapping subproblems? → **DP** (memoization/tabulation) brings it down.
   - Am I sorting when I don't need to (O(n log n) where O(n) would do with a hash map)?

### Example: turning O(n²) into O(n) with a hash map (Two Sum)
```python
def two_sum(nums, target):
    seen = {}                      # value -> index
    for i, n in enumerate(nums):
        complement = target - n
        if complement in seen:     # O(1) lookup instead of O(n) inner loop
            return [seen[complement], i]
        seen[n] = i
    return []
# Time: O(n)   Space: O(n)  — vs brute force O(n^2) time, O(1) space.
# Always name this trade-off explicitly: we spent O(n) space to buy O(n) time.
```
