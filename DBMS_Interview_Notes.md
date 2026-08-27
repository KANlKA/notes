# DBMS Interview Notes (Coupa-ready)

---

## 1. Fundamentals

**Database** — An organized, persistent collection of related data, stored so it can be efficiently accessed, managed, and updated.

**DBMS (Database Management System)** — Software layer that lets users/applications create, read, update, delete, and manage data without dealing with low-level storage details. Handles concurrency, security, backup/recovery, and data integrity. Examples: MySQL, PostgreSQL, Oracle, SQL Server, MongoDB (non-relational).

**RDBMS (Relational DBMS)** — A DBMS based on the relational model: data stored in tables (relations) with rows and columns, related via keys, and manipulated using SQL. Enforces schema and constraints (PK, FK, unique, not null, check). Examples: MySQL, PostgreSQL, Oracle, SQL Server.

**Table** — A collection of related rows sharing the same set of columns (attributes). Represents one entity type, e.g., `Employees`.

**Row (Tuple/Record)** — A single, complete entry in a table — one instance of the entity.

**Column (Attribute/Field)** — A named property common to all rows, with a defined data type (INT, VARCHAR, DATE, etc.).

**Schema** — The logical blueprint of a database: tables, columns, data types, constraints, and relationships. Two senses:
- Logical schema: overall structure/design of the DB.
- Namespace schema (in Postgres/Oracle): a container grouping tables, e.g., `public.employees`.

```sql
CREATE TABLE employees (
    emp_id      INT PRIMARY KEY,
    first_name  VARCHAR(50) NOT NULL,
    last_name   VARCHAR(50) NOT NULL,
    dept_id     INT,
    salary      DECIMAL(10,2) CHECK (salary >= 0),
    hire_date   DATE DEFAULT CURRENT_DATE
);
```

---

## 2. Keys

| Key | Definition |
|---|---|
| **Super key** | Any set of one or more columns that can uniquely identify a row. May contain extra (redundant) columns. |
| **Candidate key** | A *minimal* super key — no subset of it is also a super key. A table can have multiple candidate keys. |
| **Primary key** | The candidate key chosen to uniquely identify rows. Cannot be NULL, must be unique, one per table. |
| **Foreign key** | A column (or set) in one table that references the primary key of another table, enforcing referential integrity. |
| **Composite key** | A primary/candidate key made up of two or more columns, where no single column alone is unique. |

```sql
-- Super key example: (emp_id), (emp_id, email), (email, ssn) are all super keys
-- Candidate keys: emp_id, email, ssn (each independently unique)
-- Primary key: chosen from candidates
CREATE TABLE employees (
    emp_id  INT PRIMARY KEY,
    email   VARCHAR(100) UNIQUE NOT NULL,   -- alternate candidate key
    ssn     CHAR(9) UNIQUE NOT NULL
);

-- Foreign key
CREATE TABLE departments (
    dept_id   INT PRIMARY KEY,
    dept_name VARCHAR(50)
);

CREATE TABLE employees2 (
    emp_id  INT PRIMARY KEY,
    name    VARCHAR(50),
    dept_id INT,
    FOREIGN KEY (dept_id) REFERENCES departments(dept_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
);

-- Composite key example: order_items needs (order_id, product_id) together to be unique
CREATE TABLE order_items (
    order_id   INT,
    product_id INT,
    quantity   INT,
    PRIMARY KEY (order_id, product_id),
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
```

**Interview tip:** Be ready to explain FK actions — `CASCADE`, `SET NULL`, `RESTRICT`, `NO ACTION` — and when you'd use each (e.g., `CASCADE` delete for order_items when an order is deleted, `RESTRICT` for preventing deletion of a department that still has employees).

---

## 3. Normalization

**Functional Dependency (FD)** — A constraint where one attribute (or set) determines another: `A → B` means for every value of A there's exactly one value of B. Example: `emp_id → emp_name`.

### 1NF (First Normal Form)
- Each column holds atomic (indivisible) values — no repeating groups or arrays.
- Each row is unique.

```
-- Violates 1NF
StudentID | Courses
1         | "Math, Physics"

-- 1NF compliant
StudentID | Course
1         | Math
1         | Physics
```

### 2NF (Second Normal Form)
- Must be in 1NF.
- No **partial dependency**: every non-key attribute must depend on the *entire* composite primary key, not just part of it. (Only relevant when PK is composite.)

```
-- Table: (StudentID, CourseID) -> InstructorName, StudentName
-- StudentName depends only on StudentID (partial dependency) => violates 2NF

-- Fix: split into
Students(StudentID, StudentName)
Enrollments(StudentID, CourseID, InstructorName)
```

### 3NF (Third Normal Form)
- Must be in 2NF.
- No **transitive dependency**: non-key attributes must depend only on the primary key, not on other non-key attributes.

```
-- Employees(EmpID, DeptID, DeptName)
-- DeptName depends on DeptID, not directly on EmpID => transitive dependency

-- Fix:
Employees(EmpID, DeptID)
Departments(DeptID, DeptName)
```

### BCNF (Boyce-Codd Normal Form) — stricter 3NF
- For every FD `A → B`, `A` must be a **super key**.
- Handles edge cases 3NF misses, typically when a table has multiple overlapping candidate keys.

```
-- Example classic case:
-- Table: (Student, Course, Instructor)
-- FDs: (Student, Course) -> Instructor   and   Instructor -> Course
-- Instructor is not a super key but determines Course => violates BCNF

-- Decompose:
Table1(Instructor, Course)
Table2(Student, Instructor)
```

### Why normalize?
- Eliminates data redundancy → saves storage.
- Prevents update/insert/delete anomalies (inconsistent data when the same fact is stored in multiple places).
- Improves data integrity.

### When denormalization makes sense
- Read-heavy analytical/reporting systems (data warehouses) where join cost > redundancy cost.
- Performance-critical paths where joins across many normalized tables are too slow.
- Precomputing aggregates (e.g., storing `order_total` on the `orders` row instead of summing `order_items` every time).
- Caching/materialized views for dashboards.
- Trade-off: faster reads, but more storage, harder consistency maintenance, and risk of anomalies — usually mitigated with triggers, scheduled jobs, or CDC pipelines.

---

## 4. Transactions — ACID

A **transaction** is a sequence of operations executed as a single logical unit of work — either fully applied or fully undone.

| Property | Meaning |
|---|---|
| **Atomicity** | All operations in a transaction succeed, or none do ("all or nothing"). |
| **Consistency** | A transaction moves the DB from one valid state to another, respecting all constraints, triggers, cascades. |
| **Isolation** | Concurrent transactions don't interfere with each other's intermediate states. |
| **Durability** | Once committed, changes survive crashes/power loss (written to non-volatile storage/WAL). |

```sql
BEGIN TRANSACTION;

UPDATE accounts SET balance = balance - 500 WHERE account_id = 1;
UPDATE accounts SET balance = balance + 500 WHERE account_id = 2;

-- If both succeed:
COMMIT;

-- If anything fails (e.g., insufficient balance):
-- ROLLBACK;
```

```sql
-- Atomicity example with error handling (Postgres/PL-pgSQL style)
BEGIN;
  UPDATE accounts SET balance = balance - 500 WHERE account_id = 1;
  -- Suppose a CHECK constraint (balance >= 0) fails here
  UPDATE accounts SET balance = balance + 500 WHERE account_id = 2;
EXCEPTION WHEN OTHERS THEN
  ROLLBACK;
END;
```

**Classic interview example:** Bank transfer — debit account A, credit account B. If the system crashes after debit but before credit, atomicity + durability via the transaction log ensures either both happen or neither does on recovery.

---

## 5. Concurrency Problems

| Anomaly | Description | Example |
|---|---|---|
| **Dirty read** | Transaction reads data written by another *uncommitted* transaction. If that transaction rolls back, the read data never really existed. | T1 updates balance to 1000 (not committed). T2 reads 1000. T1 rolls back to 500. T2 has "dirty" data. |
| **Non-repeatable read** | Same query, run twice in one transaction, returns different values because another transaction committed an update in between. | T1 reads balance=500. T2 updates & commits balance=700. T1 reads again → 700. |
| **Phantom read** | Same query, run twice, returns a different *set of rows* because another transaction inserted/deleted rows matching the condition. | T1 runs `SELECT * FROM orders WHERE amount>100` → 5 rows. T2 inserts a new matching order & commits. T1 re-runs → 6 rows. |
| **Lost update** | Two transactions read the same value, both update it independently, and one update overwrites the other, "losing" it. | T1 reads stock=10. T2 reads stock=10. T1 sets stock=9 (sold 1), commits. T2 sets stock=9 (sold 1), commits — but 2 items were actually sold, stock should be 8. |

```sql
-- Lost update prevention example (optimistic locking with a version column)
UPDATE products
SET stock = stock - 1, version = version + 1
WHERE product_id = 101 AND version = 5;
-- If 0 rows affected, someone else updated it first -> retry/reload
```

---

## 6. Isolation Levels

Isolation levels trade off consistency vs concurrency/performance. Standard SQL levels (low → high strictness):

| Level | Dirty Read | Non-repeatable Read | Phantom Read | Notes |
|---|---|---|---|---|
| **Read Uncommitted** | Possible | Possible | Possible | Lowest isolation, highest concurrency. Rarely used. |
| **Read Committed** | Prevented | Possible | Possible | Default in PostgreSQL, Oracle, SQL Server. Each statement sees only committed data. |
| **Repeatable Read** | Prevented | Prevented | Possible (standard SQL) / Prevented in practice (MySQL InnoDB via gap locks / Postgres via snapshot) | Default in MySQL InnoDB. |
| **Serializable** | Prevented | Prevented | Prevented | Highest isolation — transactions behave as if executed one after another. Lowest concurrency. |

```sql
-- Setting isolation level (Postgres syntax)
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SELECT * FROM accounts WHERE account_id = 1;
UPDATE accounts SET balance = balance - 100 WHERE account_id = 1;
COMMIT;

-- MySQL syntax
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
```

**Interview tip:** Know that PostgreSQL implements Repeatable Read/Serializable via MVCC (snapshot isolation) rather than pure locking — good follow-up talking point.

---

## 7. Indexing

**Why indexes?** Without an index, the DB must scan the whole table (full table scan) to find matching rows. An index is an auxiliary data structure that lets the engine locate rows in O(log n) instead of O(n), at the cost of extra storage and slower writes (index must be updated on INSERT/UPDATE/DELETE).

### B-Tree
- Balanced tree structure; internal nodes store keys to guide search, all operations (search/insert/delete) are O(log n).
- Good for range queries and equality lookups (`<`, `>`, `BETWEEN`, `=`).

### B+ Tree (used by most RDBMS indexes, e.g., MySQL InnoDB)
- Variant of B-tree: **only leaf nodes store actual data (or pointers to rows)**; internal nodes store only keys for navigation.
- Leaf nodes are linked in a sorted linked list → very efficient for range scans and ordered traversal (`ORDER BY`, `BETWEEN`).
- More fanout per node than B-tree (since internal nodes don't carry data) → shallower tree → fewer disk reads.

### Clustered vs Non-clustered index
| | Clustered | Non-clustered |
|---|---|---|
| Data storage | Table rows are physically stored in index order | Separate structure; leaf nodes store a pointer/row-id back to the actual row |
| Count per table | Only 1 (data can only be sorted one way physically) | Many allowed |
| Example | Primary key in InnoDB (MySQL) — table *is* the B+ tree | Any secondary index, e.g., an index on `email` |
| Lookup cost | Direct — index leaf = data | Extra hop: find pointer, then fetch actual row ("bookmark lookup") |

```sql
-- Clustered index is typically the primary key (implicit in InnoDB)
CREATE TABLE orders (
    order_id INT PRIMARY KEY,   -- clustered index in InnoDB
    customer_id INT,
    order_date DATE,
    status VARCHAR(20)
);

-- Non-clustered (secondary) index
CREATE INDEX idx_customer_id ON orders(customer_id);

-- Composite index
CREATE INDEX idx_customer_status ON orders(customer_id, status);
```

### Composite indexes
- Index on multiple columns, e.g., `(customer_id, status)`.
- **Leftmost prefix rule**: the index can be used for queries filtering on `customer_id` alone, or `customer_id + status`, but NOT for `status` alone.

```sql
-- Uses idx_customer_status fully
SELECT * FROM orders WHERE customer_id = 5 AND status = 'shipped';

-- Uses idx_customer_status partially (only customer_id part)
SELECT * FROM orders WHERE customer_id = 5;

-- Cannot use idx_customer_status (status is not the leftmost column)
SELECT * FROM orders WHERE status = 'shipped';
```

### Index trade-offs
- **Pros:** Much faster SELECT/WHERE/JOIN/ORDER BY/GROUP BY on indexed columns.
- **Cons:**
  - Slower INSERT/UPDATE/DELETE (index must be maintained).
  - Extra disk/memory usage.
  - Too many indexes can confuse the query optimizer and bloat write latency.
- **Best practice:** Index columns used in WHERE, JOIN, ORDER BY, and high-selectivity columns (many distinct values). Avoid indexing low-selectivity columns (e.g., a boolean `is_active`) alone.

---

## 8. Joins

Setup for examples:
```sql
CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    name VARCHAR(50)
);

CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    amount DECIMAL(10,2),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);
```

**Inner Join** — only rows with matches in both tables.
```sql
SELECT c.name, o.amount
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id;
```

**Left (Outer) Join** — all rows from left table, matched rows from right (NULL if no match).
```sql
SELECT c.name, o.amount
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id;
-- Customers with no orders still appear, with o.amount = NULL
```

**Right (Outer) Join** — all rows from right table, matched rows from left.
```sql
SELECT c.name, o.amount
FROM customers c
RIGHT JOIN orders o ON c.customer_id = o.customer_id;
```

**Full (Outer) Join** — all rows from both, matched where possible, NULLs elsewhere.
```sql
SELECT c.name, o.amount
FROM customers c
FULL OUTER JOIN orders o ON c.customer_id = o.customer_id;
-- MySQL lacks FULL JOIN natively; simulate with UNION of LEFT and RIGHT JOIN
SELECT c.name, o.amount FROM customers c LEFT JOIN orders o ON c.customer_id = o.customer_id
UNION
SELECT c.name, o.amount FROM customers c RIGHT JOIN orders o ON c.customer_id = o.customer_id;
```

**Self Join** — a table joined with itself, e.g., employee-manager hierarchy.
```sql
CREATE TABLE employees (
    emp_id INT PRIMARY KEY,
    name VARCHAR(50),
    manager_id INT
);

SELECT e.name AS employee, m.name AS manager
FROM employees e
LEFT JOIN employees m ON e.manager_id = m.emp_id;
```

**Cross Join** — Cartesian product; every row of table A paired with every row of table B.
```sql
SELECT s.size, c.color
FROM sizes s
CROSS JOIN colors c;
-- Useful for generating all size/color combinations for a product catalog
```

**Interview tip:** Be ready to explain the difference between `WHERE` filtering after a `LEFT JOIN` (which can silently convert it into an INNER JOIN if you filter on the right table's column without handling NULLs) vs filtering inside the `ON` clause.

---

## 9. Database Design — Schema Examples

### A. Hospital Management System

```sql
CREATE TABLE departments (
    dept_id     INT PRIMARY KEY,
    dept_name   VARCHAR(50) NOT NULL
);

CREATE TABLE doctors (
    doctor_id   INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    specialization VARCHAR(50),
    dept_id     INT,
    FOREIGN KEY (dept_id) REFERENCES departments(dept_id)
);

CREATE TABLE patients (
    patient_id  INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    dob         DATE,
    gender      CHAR(1),
    phone       VARCHAR(15)
);

CREATE TABLE appointments (
    appointment_id INT PRIMARY KEY,
    patient_id  INT NOT NULL,
    doctor_id   INT NOT NULL,
    appt_date   DATETIME NOT NULL,
    status      VARCHAR(20) DEFAULT 'scheduled',
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
    FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
);

CREATE TABLE prescriptions (
    prescription_id INT PRIMARY KEY,
    appointment_id  INT NOT NULL,
    medicine_name   VARCHAR(100),
    dosage          VARCHAR(50),
    FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
);

CREATE TABLE billing (
    bill_id     INT PRIMARY KEY,
    patient_id  INT NOT NULL,
    appointment_id INT,
    amount      DECIMAL(10,2) NOT NULL,
    paid        BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
    FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
);
```
**Design notes:** doctor↔department = many-to-one; patient↔doctor via `appointments` = many-to-many resolved with a junction/fact table holding appointment-specific attributes (date, status). Consider a `rooms`/`beds` table with foreign key to `admissions` for inpatients.

---

### B. E-commerce System

```sql
CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(100) UNIQUE NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    category_id INT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL
);

CREATE TABLE products (
    product_id  INT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    category_id INT,
    price       DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    stock_qty   INT NOT NULL DEFAULT 0,
    FOREIGN KEY (category_id) REFERENCES categories(category_id)
);

CREATE TABLE addresses (
    address_id  INT PRIMARY KEY,
    customer_id INT NOT NULL,
    line1       VARCHAR(150),
    city        VARCHAR(50),
    state       VARCHAR(50),
    pincode     VARCHAR(10),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE TABLE orders (
    order_id    INT PRIMARY KEY,
    customer_id INT NOT NULL,
    address_id  INT NOT NULL,
    order_date  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status      VARCHAR(20) DEFAULT 'pending',
    total_amount DECIMAL(10,2),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (address_id) REFERENCES addresses(address_id)
);

CREATE TABLE order_items (
    order_id    INT,
    product_id  INT,
    quantity    INT NOT NULL CHECK (quantity > 0),
    unit_price  DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (order_id, product_id),
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE payments (
    payment_id  INT PRIMARY KEY,
    order_id    INT NOT NULL,
    amount      DECIMAL(10,2) NOT NULL,
    method      VARCHAR(20),
    status      VARCHAR(20) DEFAULT 'pending',
    paid_at     TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
);
```
**Design notes:** `order_items` is the classic many-to-many junction (orders↔products) carrying `quantity` and a *price snapshot* (`unit_price`) so historical orders aren't affected by future price changes (denormalization done intentionally for correctness). `total_amount` on `orders` is a denormalized aggregate for fast reads, kept in sync via app logic or a trigger.

---

### C. Library Management System

```sql
CREATE TABLE authors (
    author_id   INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL
);

CREATE TABLE books (
    book_id     INT PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    isbn        VARCHAR(20) UNIQUE,
    total_copies INT DEFAULT 1,
    available_copies INT DEFAULT 1
);

CREATE TABLE book_authors (   -- many-to-many junction
    book_id     INT,
    author_id   INT,
    PRIMARY KEY (book_id, author_id),
    FOREIGN KEY (book_id) REFERENCES books(book_id),
    FOREIGN KEY (author_id) REFERENCES authors(author_id)
);

CREATE TABLE members (
    member_id   INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(100) UNIQUE,
    joined_on   DATE DEFAULT CURRENT_DATE
);

CREATE TABLE loans (
    loan_id     INT PRIMARY KEY,
    book_id     INT NOT NULL,
    member_id   INT NOT NULL,
    issue_date  DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date    DATE NOT NULL,
    return_date DATE,
    FOREIGN KEY (book_id) REFERENCES books(book_id),
    FOREIGN KEY (member_id) REFERENCES members(member_id)
);

CREATE TABLE fines (
    fine_id     INT PRIMARY KEY,
    loan_id     INT NOT NULL,
    amount      DECIMAL(6,2) NOT NULL,
    paid        BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (loan_id) REFERENCES loans(loan_id)
);
```
**Design notes:** `book_authors` handles the many-to-many between books and authors (a book can have multiple authors, an author can write multiple books). `available_copies` is a denormalized counter updated on issue/return for fast availability checks, instead of counting active loans every time.

---

### D. Parking Management System

```sql
CREATE TABLE parking_lots (
    lot_id      INT PRIMARY KEY,
    name        VARCHAR(100),
    location    VARCHAR(150)
);

CREATE TABLE parking_spots (
    spot_id     INT PRIMARY KEY,
    lot_id      INT NOT NULL,
    spot_type   VARCHAR(20),   -- 'compact','large','handicap','bike'
    is_occupied BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (lot_id) REFERENCES parking_lots(lot_id)
);

CREATE TABLE vehicles (
    vehicle_id  INT PRIMARY KEY,
    license_plate VARCHAR(20) UNIQUE NOT NULL,
    vehicle_type VARCHAR(20)  -- 'car','bike','truck'
);

CREATE TABLE tickets (
    ticket_id   INT PRIMARY KEY,
    vehicle_id  INT NOT NULL,
    spot_id     INT NOT NULL,
    entry_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    exit_time   TIMESTAMP,
    amount_due  DECIMAL(8,2),
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id),
    FOREIGN KEY (spot_id) REFERENCES parking_spots(spot_id)
);

CREATE TABLE payments (
    payment_id  INT PRIMARY KEY,
    ticket_id   INT NOT NULL,
    amount      DECIMAL(8,2),
    method      VARCHAR(20),
    paid_at     TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id)
);
```
**Design notes:** `is_occupied` on spots is a fast-lookup denormalized flag; the source of truth is really "is there an open ticket (no `exit_time`) for this spot." Concurrency matters here — assigning a spot to two vehicles simultaneously is a classic race condition, solved with row-level locking (`SELECT ... FOR UPDATE`) or a unique constraint pattern.

```sql
-- Safe spot assignment example (Postgres)
BEGIN;
SELECT spot_id FROM parking_spots
WHERE lot_id = 1 AND is_occupied = FALSE
LIMIT 1
FOR UPDATE SKIP LOCKED;

UPDATE parking_spots SET is_occupied = TRUE WHERE spot_id = :spot_id;
INSERT INTO tickets (vehicle_id, spot_id) VALUES (:vehicle_id, :spot_id);
COMMIT;
```

---

### E. Payment System

```sql
CREATE TABLE users (
    user_id     INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE accounts (
    account_id  INT PRIMARY KEY,
    user_id     INT NOT NULL,
    balance     DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency    CHAR(3) DEFAULT 'USD',
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE transactions (
    txn_id      INT PRIMARY KEY,
    from_account INT,
    to_account   INT,
    amount       DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    status       VARCHAR(20) DEFAULT 'pending', -- pending, completed, failed, reversed
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_account) REFERENCES accounts(account_id),
    FOREIGN KEY (to_account) REFERENCES accounts(account_id)
);

CREATE TABLE payment_methods (
    method_id   INT PRIMARY KEY,
    user_id     INT NOT NULL,
    type        VARCHAR(20),  -- 'card','bank','wallet'
    details     VARCHAR(200), -- tokenized, never store raw card numbers
    is_default  BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE audit_log (
    log_id      INT PRIMARY KEY,
    txn_id      INT NOT NULL,
    event       VARCHAR(50),
    event_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (txn_id) REFERENCES transactions(txn_id)
);
```
**Design notes / talking points (very relevant for Coupa — a spend-management/payments company):**
- Fund transfer between two accounts **must be atomic** — wrap debit + credit in a single transaction; use `SERIALIZABLE` or row locking (`SELECT ... FOR UPDATE`) to prevent lost updates on `balance`.
- Never do `balance = balance - amount` without a `CHECK (balance >= 0)` and a transaction, to avoid overdrafts under concurrent transfers.
- `audit_log` gives an immutable trail for compliance (important in fintech/spend-management).
- Idempotency: payment APIs should accept an idempotency key so retried requests don't double-charge — often modeled as a unique constraint on `(idempotency_key)` in `transactions`.

```sql
-- Atomic transfer with row locking (Postgres)
BEGIN;
SELECT balance FROM accounts WHERE account_id = 1 FOR UPDATE;
SELECT balance FROM accounts WHERE account_id = 2 FOR UPDATE;

UPDATE accounts SET balance = balance - 100 WHERE account_id = 1;
UPDATE accounts SET balance = balance + 100 WHERE account_id = 2;

INSERT INTO transactions (from_account, to_account, amount, status)
VALUES (1, 2, 100, 'completed');
COMMIT;
```

---

## 10. Quick Interview Recap Cheat-Sheet

- **DBMS vs RDBMS**: RDBMS enforces the relational model + constraints; DBMS is the general umbrella (includes NoSQL).
- **Normalization goal**: remove redundancy/anomalies; **denormalize** for read-heavy performance.
- **ACID**: Atomicity, Consistency, Isolation, Durability — know a bank-transfer example cold.
- **Isolation levels** trade correctness for concurrency; know which anomalies each level prevents.
- **B+ tree** is why range queries and `ORDER BY` are fast on indexed columns; **leftmost prefix rule** for composite indexes.
- **Joins**: know how to whiteboard Venn diagrams for inner/left/right/full, and self-join for hierarchies.
- **Schema design**: always identify entities → relationships (1:1, 1:N, N:M) → junction tables for N:M → decide what (if anything) to denormalize for performance, and justify it.
