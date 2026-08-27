# SQL Interview Notes (Coupa-ready)

Sample schema used throughout (assume this unless a section defines its own):

```sql
CREATE TABLE departments (
    dept_id   INT PRIMARY KEY,
    dept_name VARCHAR(50)
);

CREATE TABLE employees (
    emp_id     INT PRIMARY KEY,
    name       VARCHAR(50),
    dept_id    INT,
    manager_id INT,          -- self-referencing FK
    salary     DECIMAL(10,2),
    hire_date  DATE,
    FOREIGN KEY (dept_id) REFERENCES departments(dept_id),
    FOREIGN KEY (manager_id) REFERENCES employees(emp_id)
);
```

---

## 1. Basics

**SELECT / WHERE**
```sql
SELECT name, salary
FROM employees
WHERE salary > 50000;
```

**DISTINCT** — removes duplicate rows from the result set.
```sql
SELECT DISTINCT dept_id FROM employees;
```

**ORDER BY**
```sql
SELECT name, salary
FROM employees
ORDER BY salary DESC, name ASC;   -- secondary sort key breaks ties
```

**LIMIT** (Postgres/MySQL) — restrict number of rows returned. (SQL Server uses `TOP`, Oracle uses `FETCH FIRST`.)
```sql
SELECT name, salary FROM employees
ORDER BY salary DESC
LIMIT 5;

-- with offset (pagination)
SELECT name, salary FROM employees
ORDER BY salary DESC
LIMIT 5 OFFSET 10;   -- rows 11-15
```

**CASE** — inline conditional logic.
```sql
SELECT name, salary,
    CASE
        WHEN salary >= 100000 THEN 'High'
        WHEN salary >= 50000  THEN 'Medium'
        ELSE 'Low'
    END AS salary_band
FROM employees;
```

---

## 2. Aggregation

| Function | Purpose |
|---|---|
| `COUNT(*)` | Number of rows |
| `COUNT(col)` | Number of non-NULL values in col |
| `SUM(col)` | Total |
| `AVG(col)` | Mean |
| `MIN(col)` / `MAX(col)` | Smallest / largest value |

```sql
SELECT
    COUNT(*)          AS total_employees,
    COUNT(manager_id)  AS employees_with_manager,  -- excludes NULLs
    SUM(salary)        AS total_payroll,
    AVG(salary)        AS avg_salary,
    MIN(salary)        AS min_salary,
    MAX(salary)        AS max_salary
FROM employees;
```

---

## 3. Grouping

**GROUP BY** — collapse rows into groups sharing a value, use with aggregate functions.
```sql
SELECT dept_id, COUNT(*) AS headcount, AVG(salary) AS avg_salary
FROM employees
GROUP BY dept_id;
```

**HAVING** — filters *after* grouping/aggregation (WHERE can't reference aggregates; HAVING can).
```sql
SELECT dept_id, AVG(salary) AS avg_salary
FROM employees
GROUP BY dept_id
HAVING AVG(salary) > 60000;
```
**Interview tip:** Execution order is `FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT`. `WHERE` filters raw rows before grouping; `HAVING` filters groups after aggregation.

---

## 4. Joins (quick recap with employees/departments)

```sql
-- INNER JOIN: only employees that have a matching department
SELECT e.name, d.dept_name
FROM employees e
INNER JOIN departments d ON e.dept_id = d.dept_id;

-- LEFT JOIN: all employees, department NULL if none assigned
SELECT e.name, d.dept_name
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.dept_id;

-- RIGHT JOIN: all departments, even empty ones
SELECT e.name, d.dept_name
FROM employees e
RIGHT JOIN departments d ON e.dept_id = d.dept_id;

-- SELF JOIN: employee -> manager name
SELECT e.name AS employee, m.name AS manager
FROM employees e
LEFT JOIN employees m ON e.manager_id = m.emp_id;
```

---

## 5. Subqueries

**Scalar subquery** — returns a single value, used anywhere a single value is expected.
```sql
SELECT name, salary
FROM employees
WHERE salary > (SELECT AVG(salary) FROM employees);
```

**Nested subquery** — a subquery inside another query's WHERE/FROM/SELECT, evaluated independently (not row-by-row dependent on the outer query).
```sql
SELECT name
FROM employees
WHERE dept_id IN (
    SELECT dept_id FROM departments WHERE dept_name = 'Engineering'
);
```

**Correlated subquery** — references a column from the outer query, so it re-executes once per outer row.
```sql
-- Employees earning more than their own department's average
SELECT e.name, e.salary, e.dept_id
FROM employees e
WHERE e.salary > (
    SELECT AVG(e2.salary)
    FROM employees e2
    WHERE e2.dept_id = e.dept_id     -- correlation to outer query
);
```
**Interview tip:** Correlated subqueries can be slow (run once per outer row) — often rewritable with a window function (`AVG(salary) OVER (PARTITION BY dept_id)`), which is usually more efficient since it's computed in a single pass.

---

## 6. CTEs (Common Table Expressions)

A `WITH` clause defines a named temporary result set, readable top-to-bottom, that can be referenced later in the same query. Improves readability over deeply nested subqueries.

```sql
WITH dept_avg AS (
    SELECT dept_id, AVG(salary) AS avg_salary
    FROM employees
    GROUP BY dept_id
)
SELECT e.name, e.salary, d.avg_salary
FROM employees e
JOIN dept_avg d ON e.dept_id = d.dept_id
WHERE e.salary > d.avg_salary;
```

**Recursive CTE** (bonus — common for org charts):
```sql
WITH RECURSIVE org_chart AS (
    -- anchor: top-level employees (no manager)
    SELECT emp_id, name, manager_id, 1 AS level
    FROM employees
    WHERE manager_id IS NULL

    UNION ALL

    -- recursive: employees reporting to someone already in org_chart
    SELECT e.emp_id, e.name, e.manager_id, oc.level + 1
    FROM employees e
    JOIN org_chart oc ON e.manager_id = oc.emp_id
)
SELECT * FROM org_chart ORDER BY level;
```

---

## 7. Window Functions ⭐ (heavily tested)

Window functions compute a value across a "window" of rows related to the current row, **without** collapsing rows like GROUP BY does.

General syntax:
```sql
function_name(...) OVER (
    PARTITION BY col1     -- optional: resets the window per group
    ORDER BY col2         -- optional: defines row order within partition
    [ROWS/RANGE frame]    -- optional: defines the sliding frame
)
```

| Function | Behavior |
|---|---|
| `ROW_NUMBER()` | Unique sequential number per row within partition (no ties — always 1,2,3,4...) |
| `RANK()` | Same rank for ties, but **skips** subsequent numbers (1,2,2,4) |
| `DENSE_RANK()` | Same rank for ties, **no gaps** (1,2,2,3) |
| `LEAD(col, n)` | Value from n rows *ahead* in the ordered partition |
| `LAG(col, n)` | Value from n rows *behind* in the ordered partition |
| `PARTITION BY` | Splits data into independent groups, each gets its own window calculation (like GROUP BY but doesn't collapse rows) |

```sql
SELECT
    name, dept_id, salary,
    ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS row_num,
    RANK()       OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rank_num,
    DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS dense_rank_num,
    LAG(salary)  OVER (PARTITION BY dept_id ORDER BY salary DESC) AS prev_salary,
    LEAD(salary) OVER (PARTITION BY dept_id ORDER BY salary DESC) AS next_salary
FROM employees;
```

Example ranking output for one department (salaries: 90k, 80k, 80k, 70k):

| salary | ROW_NUMBER | RANK | DENSE_RANK |
|---|---|---|---|
| 90000 | 1 | 1 | 1 |
| 80000 | 2 | 2 | 2 |
| 80000 | 3 | 2 | 2 |
| 70000 | 4 | 4 | 3 |

---

## 8. Must-Solve Problems

### 1. Second highest salary

```sql
-- Method 1: LIMIT/OFFSET
SELECT DISTINCT salary
FROM employees
ORDER BY salary DESC
LIMIT 1 OFFSET 1;

-- Method 2: Subquery (portable across most DBs)
SELECT MAX(salary) AS second_highest
FROM employees
WHERE salary < (SELECT MAX(salary) FROM employees);

-- Method 3: DENSE_RANK (best for handling ties correctly, extends easily to Nth)
SELECT salary
FROM (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM employees
) t
WHERE rnk = 2;
```

### 2. Nth highest salary

```sql
-- Generalized with DENSE_RANK (use DENSE_RANK, not ROW_NUMBER, so ties don't skew results)
SELECT salary
FROM (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM employees
) t
WHERE rnk = :N;   -- e.g. :N = 3 for 3rd highest

-- MySQL alternative without window functions
SELECT DISTINCT salary
FROM employees e1
WHERE (:N - 1) = (
    SELECT COUNT(DISTINCT salary)
    FROM employees e2
    WHERE e2.salary > e1.salary
);
```

### 3. Top N per department

```sql
-- Top 2 highest-paid employees per department
SELECT *
FROM (
    SELECT e.*,
           DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rnk
    FROM employees e
) ranked
WHERE rnk <= 2;
```

### 4. Employees earning above their department's average

```sql
-- Using a window function (single pass, efficient)
SELECT name, dept_id, salary
FROM (
    SELECT name, dept_id, salary,
           AVG(salary) OVER (PARTITION BY dept_id) AS dept_avg
    FROM employees
) t
WHERE salary > dept_avg;

-- Using a correlated subquery (equivalent, less efficient)
SELECT e.name, e.dept_id, e.salary
FROM employees e
WHERE e.salary > (
    SELECT AVG(e2.salary) FROM employees e2 WHERE e2.dept_id = e.dept_id
);
```

### 5. Duplicate records

```sql
-- Find duplicates based on (name, dept_id) — appearing more than once
SELECT name, dept_id, COUNT(*) AS cnt
FROM employees
GROUP BY name, dept_id
HAVING COUNT(*) > 1;

-- Delete duplicates, keeping the row with the lowest emp_id (Postgres)
DELETE FROM employees a
USING employees b
WHERE a.emp_id > b.emp_id
  AND a.name = b.name
  AND a.dept_id = b.dept_id;

-- Delete duplicates using ROW_NUMBER (portable pattern, works in most RDBMS via CTE)
WITH ranked AS (
    SELECT emp_id,
           ROW_NUMBER() OVER (PARTITION BY name, dept_id ORDER BY emp_id) AS rn
    FROM employees
)
DELETE FROM employees
WHERE emp_id IN (SELECT emp_id FROM ranked WHERE rn > 1);
```

### 6. Employees with no manager

```sql
SELECT name
FROM employees
WHERE manager_id IS NULL;
```

### 7. Customers with no orders

```sql
-- Method 1: LEFT JOIN + IS NULL
SELECT c.customer_id, c.name
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE o.order_id IS NULL;

-- Method 2: NOT EXISTS (often preferred — avoids join fan-out issues, short-circuits)
SELECT c.customer_id, c.name
FROM customers c
WHERE NOT EXISTS (
    SELECT 1 FROM orders o WHERE o.customer_id = c.customer_id
);

-- Method 3: NOT IN (careful: breaks if orders.customer_id can be NULL)
SELECT c.customer_id, c.name
FROM customers c
WHERE c.customer_id NOT IN (
    SELECT customer_id FROM orders WHERE customer_id IS NOT NULL
);
```

### 8. Department-wise maximum (salary)

```sql
-- Just the max value per department
SELECT dept_id, MAX(salary) AS max_salary
FROM employees
GROUP BY dept_id;

-- Full employee row(s) that hold the department max (GROUP BY alone can't return this safely)
SELECT e.*
FROM employees e
JOIN (
    SELECT dept_id, MAX(salary) AS max_salary
    FROM employees
    GROUP BY dept_id
) m ON e.dept_id = m.dept_id AND e.salary = m.max_salary;

-- Window function alternative
SELECT *
FROM (
    SELECT e.*, RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rnk
    FROM employees e
) t
WHERE rnk = 1;
```

### 9. Running total (cumulative sum)

```sql
-- Running total of salary ordered by hire_date
SELECT
    name, hire_date, salary,
    SUM(salary) OVER (ORDER BY hire_date
                       ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
FROM employees;

-- Running total per department
SELECT
    name, dept_id, hire_date, salary,
    SUM(salary) OVER (PARTITION BY dept_id ORDER BY hire_date
                       ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS dept_running_total
FROM employees;
```

### 10. Ranking

```sql
-- Rank employees by salary company-wide, ties get the same rank
SELECT name, salary,
       RANK() OVER (ORDER BY salary DESC) AS salary_rank
FROM employees;

-- Rank within each department
SELECT name, dept_id, salary,
       DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS dept_rank
FROM employees;
```

---

## 9. Execution Order Cheat Sheet

Actual logical processing order (differs from how you *write* the query):

```
1. FROM / JOIN
2. WHERE
3. GROUP BY
4. HAVING
5. SELECT (incl. window functions)
6. DISTINCT
7. ORDER BY
8. LIMIT / OFFSET
```

**Key gotcha:** You can't use a `SELECT` column alias in `WHERE` (alias doesn't exist yet at that stage) but you generally *can* in `ORDER BY` (runs after SELECT). Window functions are computed at the SELECT stage — after WHERE/GROUP BY/HAVING — which is why you can't filter directly on a window function result in the same query; you must wrap it in a subquery/CTE (as shown in nearly every "must-solve" example above).

---

## 10. Fast Recall Table

| Ask | Go-to tool |
|---|---|
| "Find Nth highest/lowest" | `DENSE_RANK()` in a subquery |
| "Top N per group" | `ROW_NUMBER()`/`DENSE_RANK()` + `PARTITION BY` |
| "Compare row to group average" | Window `AVG() OVER (PARTITION BY ...)` |
| "Find missing / no-match rows" | `LEFT JOIN ... IS NULL` or `NOT EXISTS` |
| "Find duplicates" | `GROUP BY ... HAVING COUNT(*) > 1` |
| "Cumulative/rolling metric" | `SUM()/AVG() OVER (ORDER BY ... ROWS BETWEEN ...)` |
| "Compare to previous/next row" | `LAG()` / `LEAD()` |
| "Hierarchical data (org chart, categories)" | Recursive CTE + self join |
