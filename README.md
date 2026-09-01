# Airbus Inventory Management System

A full-stack demo inventory system for aircraft parts: Spring Boot (Java 17, Spring MVC,
Spring JDBC / `JdbcTemplate`, Spring Security + JWT, MySQL 8) on the backend, and Angular 12
+ Angular Material + Bootstrap on the frontend.

Runs entirely locally against a local MySQL instance — no paid services, no cloud dependencies.

## Architecture overview

```
airbus-inventory-backend/     Spring Boot API, port 8080
  Controller -> Service -> Repository (JdbcTemplate DAO) -> MySQL
  JWT filter secures everything except /api/auth/**
  Global @RestControllerAdvice returns clean JSON error bodies

airbus-inventory-frontend/    Angular 12 SPA, port 4200
  LoginComponent -> AuthService -> HttpInterceptor attaches JWT to every request
  ProductListComponent (MatTable) -> ProductService -> REST API
  AuthGuard redirects to /login when there's no valid token
```

- **Backend** is a classic layered Spring MVC app. There is **no ORM/Hibernate** — persistence
  is done with `JdbcTemplate` and hand-written SQL in the repository layer, with `schema.sql` /
  `data.sql` creating and seeding the database on every startup.
- **Auth** is stateless JWT: `/api/auth/login` and `/api/auth/register` issue a signed token;
  every other endpoint requires `Authorization: Bearer <token>` and is validated by a custom
  `OncePerRequestFilter` before Spring Security's filter chain.
- **Authorization** is role-based on top of that: both `ADMIN` and `USER` can read products, but
  create/update/delete are restricted to `ADMIN` via `@PreAuthorize("hasRole('ADMIN')")` on
  `ProductService` (method-level security, enabled with `@EnableMethodSecurity`). A `USER` token
  gets a clean 403 from the same `GlobalExceptionHandler`, not a stack trace. The frontend
  mirrors this — `ProductListComponent` hides the "Add Product" button and the entire Actions
  column for non-admins, so the UI never offers an action the backend would reject.
- **Low-stock tracking** is a small domain-specific feature on top of the existing
  `reorder_level` column: `GET /api/products/low-stock` returns products where
  `quantity <= reorder_level`, ordered by how far below reorder level they are. The product
  table has a "Low stock only" toggle that switches to this endpoint.
- **Audit trail**: every product row carries `created_by`/`updated_by`, populated from the
  authenticated user's username (`SecurityContextHolder`) on create/update. Shown as a small
  "Added by X · last updated by Y" line in the edit dialog rather than as extra table columns.
- **Refresh tokens**: login/register/refresh all return an access token (1hr) and a refresh
  token (7 days, `type: refresh` claim). `AuthGuard` silently renews an expired access token
  using a still-valid refresh token before bouncing to `/login`; `AuthInterceptor` does the same
  reactively on a mid-session 401 (one retry, then logout on failure — see the interceptor's own
  comment for why this isn't request-deduplicated).
- **Pagination**: `GET /api/products` takes `page`/`size` query params and returns
  `{content, page, size, totalElements, totalPages}` instead of a bare array — real
  `LIMIT ?/OFFSET ?` server-side pagination, wired to a `MatPaginator`. `/category/**` and
  `/low-stock` stay unpaginated (smaller, targeted result sets) — see
  [Pagination scope](#pagination-scope) below.
- **Dashboard summary**: `GET /api/products/summary` returns total product count, total
  inventory value, low-stock count, and a per-category breakdown in one call, rendered as stat
  tiles above the product table (the low-stock tile is clickable — it flips the low-stock
  toggle).
- **API docs**: Swagger UI at `/swagger-ui/index.html`, OpenAPI JSON at `/v3/api-docs` — both
  public, JWT bearer auth wired in so you can "Authorize" and call protected endpoints from the
  browser.
- **Frontend** talks to the backend via absolute URLs (`http://localhost:8080/api/...`), not an
  `ng serve` proxy — see [Frontend setup](#frontend-setup) for why, and why CORS is enabled on
  the backend as a result.

## Prerequisites

| Tool | Version used in this build | Notes |
|---|---|---|
| Java (JDK) | 17 | `brew install openjdk@17` |
| Maven | 3.9.x | `brew install maven` |
| MySQL | 8.0 | `brew install mysql@8.0` |
| Node.js | 18.x | Angular 12's toolchain (webpack 4/5-era) does not run on Node 20+/23 — see caveat below |
| npm | 9.x/10.x | ships with Node |
| Angular CLI | 12.x | invoked via `npx`, not required globally |

**Node version caveat:** Angular 12 was released in 2021 and its build toolchain predates
Node's move to OpenSSL 3 (Node 17+). If your default `node -v` is 18 or newer, Angular 12's
webpack build will throw `error:0308010C:digital envelope routines::unsupported` unless you
either (a) use Node 16, or (b) set `NODE_OPTIONS=--openssl-legacy-provider` before running any
`ng` command, which is what this project's `npm start` script does automatically (see
[Frontend setup](#frontend-setup)). This machine did not have Node 16 available via Homebrew
(it's EOL and removed from formulae), so Node 18 + the legacy-provider flag was used instead —
flagged here per the "flag any deviation" instruction, since the prompt didn't specify a Node
version and Angular 12 predates Node 18's release.

## MySQL setup

1. Start MySQL (Homebrew):
   ```bash
   brew services start mysql@8.0
   ```
2. Create the database and a scoped application user (do **not** point the app at `root` in
   any real setup):
   ```bash
   /opt/homebrew/opt/mysql@8.0/bin/mysql -u root <<'EOF'
   CREATE DATABASE IF NOT EXISTS airbus_inventory;
   CREATE USER IF NOT EXISTS 'airbus_app'@'localhost' IDENTIFIED BY 'airbus_app_pw';
   GRANT ALL PRIVILEGES ON airbus_inventory.* TO 'airbus_app'@'localhost';
   FLUSH PRIVILEGES;
   EOF
   ```
3. That's it — tables and seed data are created automatically by Spring Boot on every backend
   startup (`schema.sql` / `data.sql`, `spring.sql.init.mode=always`). Tables are dropped and
   recreated on each restart so the demo always boots into the same known state (~38 seeded
   aircraft parts across 5 categories, plus 2 demo users). Any products you add during a demo
   session will be reset if you restart the backend — that's intentional for a repeatable demo,
   not a bug.

If you use a different MySQL username/password/host, override them via environment variables
instead of editing `application.properties` (see below).

## Backend setup

```bash
cd airbus-inventory-backend
mvn spring-boot:run
```

Starts on **http://localhost:8080**. Configuration (all overridable via env vars, none
hardcoded as secrets in source):

| Property | Env var | Default |
|---|---|---|
| DB URL | `DB_URL` | `jdbc:mysql://localhost:3306/airbus_inventory` |
| DB username | `DB_USERNAME` | `airbus_app` |
| DB password | `DB_PASSWORD` | `airbus_app_pw` |
| JWT secret (base64) | `JWT_SECRET` | a demo-only default baked into `application.properties` |
| JWT expiry (ms) | `JWT_EXPIRATION_MS` | `3600000` (1 hour) |
| Refresh token expiry (ms) | `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 days) |
| CORS allowed origin | `CORS_ALLOWED_ORIGIN` | `http://localhost:4200` |

For anything beyond local demo use, set `JWT_SECRET` to your own base64-encoded random value
via an environment variable — never commit a real secret to `application.properties`.

## Frontend setup

```bash
cd airbus-inventory-frontend
npm install
npm start
```

`npm start` runs `ng serve` with `NODE_OPTIONS=--openssl-legacy-provider` set, so it works on
modern Node without you having to remember the flag. Starts on **http://localhost:4200**.

**Backend URL — full URL, not a proxy:** the frontend calls the backend via an absolute
`environment.apiUrl` (`http://localhost:8080/api`), not Angular CLI's dev-server proxy
(`proxy.conf.json`). This was a deliberate choice, not an oversight: the assignment explicitly
asks for CORS to be enabled on the backend for `http://localhost:4200`, which only matters if
the browser treats the two apps as different origins — a proxy would hide that entirely by
making requests same-origin from the browser's point of view. Using absolute URLs means the
CORS configuration in `SecurityConfig` is actually exercised, not dead code.

**Token storage caveat:** the JWT is stored in `localStorage` and attached to every request by
`AuthInterceptor`. This is documented, not hidden: `localStorage` is readable by any JS running
on the page, so it's vulnerable to theft via XSS. The safer alternative — the backend setting
the token as an `HttpOnly`, `Secure`, `SameSite` cookie — was not used here because it would
require the backend to also handle CSRF protection and cookie-based session semantics, adding
complexity that isn't the point of this demo. This tradeoff is called out in
[`auth.service.ts`](airbus-inventory-frontend/src/app/services/auth.service.ts) as well.

### Pagination scope

`GET /api/products` defaults to `page=0&size=50` — larger than the 38 seeded rows, so by default
the product list still shows (and searches/sorts across) everything in one page, exactly like
before pagination existed. Pick a smaller page size from the `MatPaginator` (10 or 20) to see
real server-side paging kick in. One consequence worth knowing: the name-search box and column
sort operate on whatever's currently loaded client-side — with the default page size that's the
whole dataset, but at a smaller page size they only apply within the current page, not across
all pages. A "real" fix would push search/sort to the server as query params; out of scope here.

## Default demo login credentials

Seeded by `data.sql` on every backend startup, or create your own via the **Create an account**
link on the login page (`POST /api/auth/register` — always creates a `USER`; the public
registration form intentionally never exposes a role field, since that would let anyone
self-promote to `ADMIN`):

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | View + add/edit/delete products |
| `manager` | `manager123` | USER | View products only (read-only UI, backend 403s on writes) |

## Running the tests

**Backend** — JUnit 5 + Mockito unit tests for the service/security/exception layers
(`ProductService`, `AuthService`, `JwtUtil`, `GlobalExceptionHandler`). Deliberately scoped to
unit tests, not a DB-backed integration suite — mocking the repositories keeps `mvn test` fast
and infra-independent (no MySQL required to run it), at the cost of not exercising the real SQL;
that's covered instead by the manual smoke test below, which does hit a real database.

```bash
cd airbus-inventory-backend
mvn test
```

**Frontend** — Jasmine/Karma specs for `AuthService`, `ProductService` (both via
`HttpClientTestingModule`, no real backend needed) and `AuthGuard` (mocked `AuthService`/
`Router`, covering the fast-path/silent-refresh/redirect branches).

```bash
cd airbus-inventory-frontend
npm test                                          # interactive, watches for changes
npx ng test --browsers=ChromeHeadless --watch=false   # single run, e.g. for CI
```

Note: a single `--watch=false` run reliably prints a benign `Some of your tests did a full page
reload!` line *after* the real results — a known Angular 12/Karma artifact from a post-run
rebuild, not a failing spec. Trust the `TOTAL: N SUCCESS` line and the process exit code, not
that trailing line.

## Running with Docker

Alternative to the local Homebrew/`mvn`/`npm` setup above — bundles MySQL, the backend, and an
nginx-served production build of the frontend:

```bash
docker compose up --build
```

Starts the same three pieces as the local setup, on the same ports (frontend :4200, backend
:8080, MySQL :3306) — stop any locally-running MySQL/backend/frontend first if you've been using
the non-Docker setup, since they'd conflict on those ports. `docker compose down` stops
everything; add `-v` to also drop the named MySQL volume (`airbus_mysql_data`) and reset to a
clean database on next `up`.

The frontend's `environment.prod.ts` bakes in `http://localhost:8080/api` at build time — this
works because the browser (on your host machine) resolves `localhost:8080` to the backend
container via the port mapping, regardless of the Docker-internal service name. It would need a
runtime-configurable API URL (e.g. an nginx `envsubst` entrypoint) to work behind a non-localhost
host or a different port mapping; out of scope for this local demo.

The frontend's Docker build uses **Node 16**, not 18 — inside a container we can pin the exact
version Angular 12 officially supports, sidestepping the `NODE_OPTIONS=--openssl-legacy-provider`
workaround the host dev setup needs (see the Node version caveat above).

## Deviations from the requested stack (flagged, with reasons)

1. **Node 18 instead of Node matching Angular 12's official support window** (12.20–16.x) —
   explained above under [Prerequisites](#prerequisites). Functionally transparent once
   `NODE_OPTIONS=--openssl-legacy-provider` is set; `npm start` sets it for you.
2. **Frontend calls the backend via absolute URL, not the Angular CLI dev proxy** — explained
   above under [Frontend setup](#frontend-setup); this exercises the CORS requirement instead
   of routing around it.
3. **`schema.sql` drops and recreates tables on every startup** rather than only on first run —
   makes the demo deterministic and idempotent (`mvn spring-boot:run` never fails on a second
   run due to duplicate seed rows) at the cost of not persisting ad-hoc changes across backend
   restarts. Documented in [MySQL setup](#mysql-setup).
4. **Refresh tokens are a second, longer-lived JWT** (`type: refresh` claim), not an opaque
   server-side token in a database table. Simpler and consistent with the rest of the app's
   stateless-JWT design, at the cost of not being revocable before it expires (no server-side
   token store to delete from) — a real production system would likely use opaque, revocable
   refresh tokens instead.
5. **Backend unit tests only, no DB-backed integration suite** — see
   [Running the tests](#running-the-tests) for the reasoning; the manual smoke test below is what
   actually exercises the real SQL end to end.
6. Everything else (Spring Boot 3.x, Spring MVC, Spring JDBC/`JdbcTemplate`, MySQL 8, JWT via
   `io.jsonwebtoken`, Bean Validation, `@RestControllerAdvice`, Angular 12, Angular Material,
   Bootstrap, Maven, Angular CLI) matches the requested stack exactly.

## Smoke test

With MySQL running and both apps started (backend on 8080, frontend on 4200):

### Via the UI
1. Open http://localhost:4200 — you're redirected to `/login` because there's no token yet
   (`AuthGuard`).
2. Click **Create an account**, register a new user, mismatch the password/confirm fields first
   to see the validation message, then fix it and submit — you're auto-logged-in and land on
   `/products` as a plain `USER` (no "Add Product" button, no Actions column).
3. Log out, log back in with `admin` / `admin123`. Above the filter bar you'll see four stat
   tiles (Total Products, Inventory Value, Low Stock Items, Categories) from
   `GET /api/products/summary`.
4. Try the name search box, the category filter, and column sorting on the product table
   (~38 seeded parts).
5. Click **Add Product**, fill in the form, submit — it appears in the table, and the stat tiles
   refresh.
6. Click the edit icon on a row — the dialog header shows "Added by … · last updated by …"
   (audit fields); change a field, save — the row and the audit line update.
7. Click the delete icon — a confirmation dialog appears; confirm and the row disappears.
8. Click the **Low Stock Items** tile (or flip the **Low stock only** toggle directly) — the
   table narrows to the parts seeded at/below their reorder level (Main Landing Gear Strut
   Assembly, Combustion Chamber Liner, Turbine Disc); the category filter disables itself while
   the toggle is on.
9. Turn the toggle back off, then use the paginator at the bottom of the table — switch the page
   size to 10 and page through; open your browser's network tab and confirm each page click
   issues a new `GET /api/products?page=…&size=10` request rather than re-slicing data already
   in memory.
10. Click **Logout**, then log back in as `manager` / `manager123` — same read-only view as
    registration produced; the low-stock toggle and pagination still work (reading is allowed
    for both roles).
11. Click **Logout** again, then try navigating back to `/products` directly — you're bounced
    back to `/login` because the token was cleared.

**Refresh-token check (optional, needs devtools):** while logged in, open your browser's
Application/Storage tab, find `airbus_auth_token` in `localStorage`, and replace its value with
any string ending the same way but with a corrupted signature (e.g. append a character). Trigger
any request (switch the category filter) — the app doesn't break: `AuthInterceptor` catches the
resulting 401, calls `/api/auth/refresh` with the still-valid refresh token, retries the original
request, and you never see an error. Check the network tab for the `401` immediately followed by
a `200` to the same URL.

### Via curl (backend only)
```bash
# 1. Protected route rejects an unauthenticated request
curl -i http://localhost:8080/api/products
# -> 401, clean JSON error body (no stack trace)

# 2. Register a new user
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_engineer","password":"demoPass1"}'

# 2b. Privilege-escalation regression check: role isn't a field on the DTO, so even trying to
# smuggle one in gets silently dropped by Jackson — this MUST come back "role":"USER"
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"attacker","password":"hackme1","role":"ADMIN"}' | python3 -c "import sys,json;print('role:', json.load(sys.stdin)['role'])"

# 3. Log in as the seeded admin and capture both tokens
LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
REFRESH=$(echo "$LOGIN" | python3 -c "import sys,json;print(json.load(sys.stdin)['refreshToken'])")

# 3b. Refresh flow: exchange the refresh token for a fresh access token
curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REFRESH\"}"

# 4. Full CRUD (products list is now paginated — page/size params, {content, totalElements, ...})
curl -s "http://localhost:8080/api/products?page=0&size=50" -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8080/api/products/category/Hydraulics" -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/products/summary -H "Authorization: Bearer $TOKEN"
NEW_ID=$(curl -s -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Test Sensor","category":"Avionics","quantity":10,"unitPrice":100.50,"supplier":"Test Supplier","reorderLevel":2}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
curl -s http://localhost:8080/api/products/$NEW_ID -H "Authorization: Bearer $TOKEN"
# response includes "createdBy":"admin","updatedBy":"admin" (audit fields)
curl -s -X PUT http://localhost:8080/api/products/$NEW_ID \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Test Sensor Updated","category":"Avionics","quantity":15,"unitPrice":120.00,"supplier":"Test Supplier","reorderLevel":3}'
curl -i -X DELETE http://localhost:8080/api/products/$NEW_ID -H "Authorization: Bearer $TOKEN"
# -> 204 No Content

# 5. Exception handler sanity checks
curl -i http://localhost:8080/api/products/$NEW_ID -H "Authorization: Bearer $TOKEN"
# -> 404, {"status":404,"message":"Product not found with id ...","timestamp":"..."}

curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"wrongpass"}'
# -> 401, {"status":401,"message":"Invalid username or password","timestamp":"..."}

# 6. Role-based authorization: a USER token can read but not write
MTOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"manager","password":"manager123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -i http://localhost:8080/api/products/low-stock -H "Authorization: Bearer $MTOKEN"
# -> 200, the 3 seeded low-stock parts
curl -i -X DELETE http://localhost:8080/api/products/1 -H "Authorization: Bearer $MTOKEN"
# -> 403, {"status":403,"message":"Access denied","timestamp":"..."}

# 7. API docs, no auth needed to view
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/swagger-ui/index.html
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/v3/api-docs
```

All of the above was run against this exact build during development — see the commit/PR
description for captured output.

## Project layout

```
Airbus/
  README.md                        (this file)
  docker-compose.yml                (mysql + backend + nginx-served frontend)
  airbus-inventory-backend/
    pom.xml
    Dockerfile                      (multi-stage: maven build -> JRE runtime)
    src/main/java/com/airbus/inventory/
      controller/    AuthController, ProductController
      service/       AuthService, ProductService
      repository/    UserRepository, ProductRepository (JdbcTemplate)
      security/      JwtUtil, JwtAuthFilter, UserDetailsServiceImpl, entry point / access denied handler
      config/        SecurityConfig (filter chain, CORS, password encoder), OpenApiConfig (Swagger)
      dto/           request/response DTOs with Bean Validation, PageResponse<T>, InventorySummaryResponse
      exception/     GlobalExceptionHandler + custom exceptions (incl. InvalidTokenException)
      model/         Product (+ audit fields), User
    src/main/resources/
      application.properties
      schema.sql
      data.sql
    src/test/java/com/airbus/inventory/   JUnit/Mockito unit tests (service, security, exception layers)
  airbus-inventory-frontend/
    Dockerfile                      (multi-stage: node build -> nginx runtime, Node 16)
    nginx.conf                      (Angular client-side routing fallback)
    src/app/
      components/    login, register, product-list, product-form (dialog), confirm-dialog
      services/      AuthService, ProductService (+ .spec.ts)
      guards/        AuthGuard (+ .spec.ts)
      interceptors/  AuthInterceptor
      models/        Product (+ audit fields, PageResponse, InventorySummary), auth, error-response
```
