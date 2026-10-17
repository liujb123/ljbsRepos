# WeBox — Enterprise Employee Meal Ordering Platform

English | [中文](README.zh-CN.md)

A full-stack meal ordering system for an in-house canteen. Around 200 employees browse the day's
menu on a phone or a laptop, order before the cut-off, and the kitchen prepares accordingly.
Administrators manage the dish catalog and publish each day's menu through a separate **Console**.

Built against `AI Vibe Coding V3.0 - PRD.md`: **Tier 1 (core) and Tier 2 (advanced) are complete**.
Tier 3 (real-time stock, AI recommendations, analytics dashboard) is out of scope; the extension
points are designed in and documented.

> **System language**: every user-facing string — interface, menu data, validation and error
> messages — is English, as PRD section 1 requires.

---

## Status

| Area | State |
| --- | --- |
| Backend (JDK 17 + Spring Boot 3.3 + standalone MySQL 8) | Implemented, running |
| Frontend (Vue 3 + TypeScript + Vite) | Implemented, typechecks and builds |
| Redis cache / idempotency / order numbering | Implemented, degradable |
| Unit tests (`mvn test`) | **56 passing** |
| End-to-end API checks (`scripts/smoke-test.ps1`) | **69 assertions passing** |
| Concurrency checks (`scripts/concurrency-test.ps1`) | **14 assertions passing** |
| Tier 3 | Not implemented — extension points documented |

---

## Feature scope

| Tier | Feature | Where |
| --- | --- | --- |
| 1 | Employee registration and sign-in, session survives refresh | `/login`, `/register` |
| 1 | Daily menu with images, prices, cuisine tags, multi-select filters, search | `/` |
| 1 | Dish detail with ingredients, allergens, spice level and live-priced options | `/menu/dishes/:id` |
| 1 | Cart with per-configuration lines, quantity editing, live total | `/cart` |
| 1 | Checkout: delivery date, meal, address, single-order guarantee | `/checkout` |
| 1 | Order history, detail with option snapshot, cancellation | `/orders` |
| 2 | Dietary preferences: allergens, cuisines, spice, taste, budget | `/settings` |
| 2 | Ordering rules: 5-portion cap, cut-off auto-switch, one order per meal | checkout + menu |
| 2 | Console: dish table, create/edit with options, on/off shelf, image upload | `/console/dishes` |
| 2 | Console: set a day's menu with per-dish quantities | `/console/daily-menu` |

---

## Technology

| Layer | Choice | Why |
| --- | --- | --- |
| Language / framework | Java 17, Spring Boot 3.3 | Required by the PRD |
| Build | Maven with the wrapper (`mvnw`) | No global Maven install needed |
| Database | **MySQL 8, standalone** | Required by the PRD; no embedded database anywhere, including tests |
| Schema | Flyway migrations | Reproducible schema; Hibernate never mutates it |
| Persistence | Spring Data JPA + Hibernate | Parameter binding removes the SQL-injection surface by default |
| Cache | **Redis** (optional accelerator) | Menu snapshot cache, idempotency fast path, order numbering |
| Security | Spring Security 6 + JWT | Stateless; the role claim maps directly to endpoint rules |
| API docs | springdoc-openapi | See `API.md` or `/swagger-ui.html` |
| Frontend | Vue 3, TypeScript, Vite | Fast dev loop, strong typing across the SPA |
| Client state | Pinia (auth, cart, preferences) + TanStack Query (server data) | Query gives caching and de-duplication for the busy menu endpoint |
| Styling | Tailwind CSS | Mobile-first breakpoints, which the two-device requirement needs |

Rejected alternatives and the reasoning behind each choice are recorded in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) section 2.

---

## Repository layout

```
webox/
├─ README.md                            this file
├─ API.md                               endpoint reference with request/response examples
├─ docker-compose.yml                   MySQL 8 + Redis 7 for a one-command start
├─ .env.example                         every configurable environment variable
├─ ai-conversations/                    raw AI coding session transcripts (PRD 7.2)
├─ docs/
│   ├─ ARCHITECTURE.md                  design decisions, data model, caching, security
│   ├─ BUSINESS-DESIGN.md               rule-by-rule implementation and the English copy table
│   └─ REQUIREMENT-TRACEABILITY.md      112 requirements → design → acceptance criteria
├─ backend/                             Spring Boot application (Maven)
│   └─ src/main/resources/db/migration/ Flyway schema
├─ frontend/                            Vue 3 SPA
└─ scripts/
    ├─ dev.ps1                          build and start both halves
    ├─ smoke-test.ps1                   69 end-to-end API assertions
    └─ concurrency-test.ps1             idempotency and meal-slot race assertions
```

---

## Prerequisites

| Dependency | Version used | Notes |
| --- | --- | --- |
| JDK | **17** (verified on 17.0.2) | Spring Boot 3 requires 17 or newer |
| MySQL | **8.0** (verified on 8.0.28) | Must be a standalone server, port 3306 |
| Redis | 5 or newer (verified on 5.0.14) | Optional, port 6379 |
| Node.js | 20+ (verified on 24.19) | For the SPA |
| Docker | any recent | Only if you use `docker-compose.yml` |

Set `JAVA_HOME` to the JDK 17 installation before building:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.2"
```

---

## Getting started

### Step 1 — Start MySQL and Redis

**Option A — Docker (one command):**

```bash
docker compose up -d
docker compose ps          # wait for webox-mysql to report healthy
```

This creates the `webox` and `webox_test` schemas with user `webox` / `webox1234`.

**Option B — existing local servers:**

```sql
CREATE DATABASE IF NOT EXISTS webox      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS webox_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'webox'@'%' IDENTIFIED BY 'webox1234';
GRANT ALL PRIVILEGES ON webox.*      TO 'webox'@'%';
GRANT ALL PRIVILEGES ON webox_test.* TO 'webox'@'%';
FLUSH PRIVILEGES;
```

Redis needs no setup. If yours requires a password, set `WEBOX_REDIS_PASSWORD` (step 2).

> **The application runs without Redis.** Verified by starting it against a dead Redis port:
> registration, the menu (all 50 dishes) and order placement all succeed, and the log records
> `Menu cache GET failed ... serving from MySQL` and `Idempotency fast path unavailable`. What
> changes is speed (every menu read hits MySQL) and one detail of the idempotency path (the
> database unique index does the work instead of the Redis fast path).
>
> `/actuator/health` therefore **excludes Redis by default**, so a fully functional instance does
> not report `503` just because Redis is absent. Set `MANAGEMENT_HEALTH_REDIS_ENABLED=true` to put
> Redis back into the health status, which is what you want in production.

### Step 2 — Configure

```bash
cp .env.example .env
```

The backend imports `.env` from the repository root or from `backend/` (whichever exists), so the
file is picked up whether you run `./mvnw spring-boot:run` or `java -jar`. Plain environment
variables work just as well and take precedence; skipping the copy entirely is fine, because every
variable has a working localhost default except `WEBOX_DB_PASSWORD` (defaults to `webox1234`) and
`WEBOX_JWT_SECRET`.

`.env` is git-ignored. If the JWT secret is empty the application generates an ephemeral one and
logs a warning — everything works, but restarting invalidates existing tokens.

See [`.env.example`](.env.example) for the full list: database, Redis, CORS origins, business time
zone, upload directory, cache TTL, and the seed account.

### Step 3 — Start the backend

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

On the first run Flyway creates the schema and the seeder populates the catalog. The service
listens on `http://localhost:8080`.

### Step 4 — Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Vite serves the SPA on `http://localhost:5173` and proxies `/api` and `/dishes` to the backend, so
the browser only ever talks to one origin.

### Step 5 — Open the app

| | URL |
| --- | --- |
| Employee app | <http://localhost:5173> |
| Console (admin) | <http://localhost:5173/console> |
| API documentation | <http://localhost:8080/swagger-ui.html> |
| Health | <http://localhost:8080/actuator/health> |

**Or start everything at once:**

```powershell
pwsh -File scripts/dev.ps1              # assumes MySQL and Redis are already running
pwsh -File scripts/dev.ps1 -WithDocker  # starts docker compose first
```

---

## Default accounts and seed data

| Item | Value |
| --- | --- |
| Administrator | `admin@webox.com` / `Admin1234` |
| Employees | register at `/register` (any valid email; no company domain restriction) |

The startup seeder is **idempotent** — restarting never duplicates data and never overwrites an
administrator's changes:

* the administrator account, if absent;
* **50 dishes**: the nine from PRD section 6 with their exact option groups, plus supplementary
  English dishes so that paging and list performance are meaningful;
* 20 bundled dish photos, bound cyclically to the catalog;
* **a published menu for today and for tomorrow**, with 50 portions per dish — so the system can be
  ordered from the moment it starts, which is what PRD 4.3 asks for.

Change the seed account with `WEBOX_SEED_ADMIN_EMAIL` / `WEBOX_SEED_ADMIN_PASSWORD`, or disable
seeding entirely with `WEBOX_SEED_ENABLED=false`.

### Resetting the environment

To start over, drop the schema and restart the backend — Flyway recreates it and the seeder
repopulates it:

```sql
DROP DATABASE webox;
CREATE DATABASE webox CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Then also clear the application's Redis keys:

```bash
redis-cli --scan --pattern 'webox:*' | xargs -r redis-cli del
```

Skipping that step is not fatal — the menu snapshot carries a 10-minute TTL and expires on its own —
but until it does, the cached menu still refers to dish ids from the previous database. This is the
one place where the cache outlives its source of truth, and the TTL is the reason it self-heals.

---

## API

The full reference is in [`API.md`](API.md); the live, interactive version is at
`/swagger-ui.html`.

Conventions in one paragraph: everything lives under `/api`; every response uses the envelope
`{ code, message, data, requestId }`; `message` is always English; money travels as a decimal
string (`"22.50"`); the employee role reaches `/api/**`, and `/api/admin/**` additionally requires
the `ADMIN` role. `POST /api/orders` requires an `Idempotency-Key` header.

---

## Tests

```bash
cd backend

./mvnw test        # 56 unit tests, no external dependencies, always runnable
```

The unit suite pins the rules that are easy to get subtly wrong:

| Test | What it locks down |
| --- | --- |
| `MealSlotResolverTest` (14) | Every cut-off boundary, including the PRD's 10:30 example, 10:00:00 and 15:00:00 exactly, and the next-day fallback |
| `CartRuleValidatorTest` (11) | Required groups, single-choice groups, foreign option ids, discontinued options, and the 3 + 3 = 6 portion rejection |
| `PriceCalculatorTest` (10) | Two-decimal rounding, `0.1 + 0.2`, repeated surcharges, and a fully hand-computed order |
| `SearchKeywordTest` (8) | `%` and `_` escaping and an inert injection-style keyword |
| `PasswordPolicyTest` (13) | Length and letter+digit boundaries |

**End-to-end and concurrency checks** run against a live server (start it first):

```powershell
pwsh -File scripts/smoke-test.ps1        # 69 assertions across every Tier 1 + Tier 2 flow
pwsh -File scripts/concurrency-test.ps1  # 14 assertions on idempotency and meal-slot races
```

The concurrency script fires genuinely parallel HTTP requests and asserts the guarantees that
matter:

```
== Idempotency under 10 parallel submissions with the same key
    statuses: 200
    distinct order numbers: 1
  [PASS] all 10 responses carry the same order number
  [PASS] 9 responses were recognised as replays
  [PASS] the database holds exactly one order for that key

== Meal-slot safety: 10 different keys racing for the same meal
    status distribution: 200x1, 409x9
  [PASS] exactly one racing submit won
  [PASS] the conflict reason is ORDER_SLOT_CONFLICT
```

---

## Architecture in brief

```
Vue 3 SPA  ──/api──▶  Spring Boot 3.3 (JDK 17)  ──JDBC──▶  MySQL 8   (system of record)
  Pinia + TanStack Query        │                              ▲
                                └──Lettuce──▶  Redis           │ never authoritative
                                              (cache · idempotency · order numbers)
```

Four decisions carry most of the design:

1. **MySQL is the only source of truth.** Redis is a degradable accelerator; losing it costs speed,
   never correctness. The fallback is to read MySQL, deliberately not an in-process map.
2. **Concurrency is enforced by unique indexes, not by locks.** `UNIQUE(user_id, client_request_id)`
   makes submission idempotent; `UNIQUE(user_id, active_slot_key)` — where `active_slot_key` is a
   generated column that becomes NULL once an order is cancelled or completed — gives MySQL the
   equivalent of a partial unique index, so an employee can have at most one active order per meal.
   There is no distributed lock anywhere.
3. **The menu is cached as one whole-day snapshot.** It is small and read by everyone at once
   during the 09:30–10:00 peak, so one cache entry serves every concurrent reader while filtering,
   search, preference ordering and paging happen in memory. Invalidation is explicit: dish writes
   clear everything, daily-menu writes clear one date, and a 10-minute TTL is the safety net.
4. **The cart lives in the browser; the server re-derives everything.** Prices, portion limits,
   option validity, the effective meal and the duplicate-order check are all recomputed from the
   database inside the placement transaction. Nothing the client sends about money is trusted.

[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) has the data model, the nine recorded decisions, the
security design and the Tier 3 extension points.

---

## AI coding session records

PRD section 7.2 requires the complete, raw export of every AI coding session used to build this
project — no screenshots, excerpts, or post-hoc summaries. The files are in
[`ai-conversations/`](ai-conversations/); see the README in that directory for what each file is
and how it was exported.

---

## Publishing this repository

**Before pushing to a public remote, remove the local credentials that the raw transcripts contain.**

The AI session records in [`ai-conversations/`](ai-conversations/) are verbatim exports, so they
include the shell commands used during development — and those commands contain this machine's
Redis password, the local MySQL root password, and the throwaway JWT secret used for local runs.
`docs/ENVIRONMENT-CHECK.md` repeats the Redis password as well. None of it is used by the
application, and none of it is a secret worth protecting beyond this machine, but it should not be
published.

Pick one:

| Option | What to do | Trade-off |
| --- | --- | --- |
| **A — keep the repo private** (recommended) | Push as-is; share the repository with the reviewer directly | Keeps the transcript 100% raw, which is what PRD 7.2 asks for |
| **B — redact and be explicit** | Replace the credential strings with `***` and state in `ai-conversations/README.md` exactly what was removed and that it was the only edit | The transcript is no longer strictly byte-for-byte raw |
| **C — exclude the exports** | Add `ai-conversations/*.jsonl` to `.gitignore` and hand the transcripts to the reviewer separately | The PRD deliverable must still be submitted, just not via the public repo |

Also rotate the Redis password if this machine is shared: it is stored in
`D:\G\Redis\redis.windows-service.conf` and was written into these files in plain text.

Everything else in the repository is safe to publish. `git status` on a fresh clone should show no
`.env`, no `*.log`, and no `backend/data/` — all three are ignored.

---

## Known limitations

| Item | Note |
| --- | --- |
| JWT in `localStorage` | Trades CSRF exposure for simplicity. Mitigated by never rendering raw HTML and by the server treating all client input as untrusted. A long-lived deployment should move to httpOnly cookies with CSRF tokens. |
| Role changes are not instant | The role travels in the token, so it takes effect within the 8-hour lifetime. Production would re-validate against the database or use a token version. |
| Uploaded images live on local disk | Fine for a single instance; object storage is the multi-instance answer. |
| Menu cache is a whole-day snapshot | Comfortable to roughly 2000 dishes; beyond that the plan is database-side paging with a filter-hash cache key, behind the same API contract. |
| Tier 3 not implemented | Real-time stock, AI recommendations and the analytics dashboard. The schema already carries `total_qty` / `sold_qty`, and the deduction would sit in the existing placement transaction as a conditional update. |

---

## Suggested demo walkthrough

1. Register a new employee, sign in, then refresh the page — the session survives.
2. Menu: multi-select `Chinese` + `Japanese`, search for `chicken`, toggle **Recommend for me**.
3. Open a dish with options (Classic Beef Burger) — the price updates as you choose.
4. Add to cart: flag `Peanuts` in Settings first to see the allergen warning; add up to 5 portions
   and watch the buttons disable with a reason; add the same dish with a different configuration
   and note it becomes a separate line.
5. Checkout: the cut-off notice appears if lunch has closed, the saved address is offered, and an
   over-budget cart shows a non-blocking warning.
6. Press **Place Order** repeatedly — exactly one order is created.
7. Return to checkout — the button now reads **View Existing Order**.
8. Cancel the order from **My Orders**, then order the same meal again.
9. Call `/api/admin/dishes` with the employee token — `403`.
10. In the Console: search and filter dishes, edit one, take one off the shelf and confirm it
    vanishes from the employee menu, then publish tomorrow's menu with quantities.
