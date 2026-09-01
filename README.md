# Airbus Inventory Management System

A full-stack inventory management system for aircraft parts, built as a portfolio-quality demo:
Spring Boot REST API on the backend, Angular SPA on the frontend, MySQL for storage. Runs
entirely on your machine: no paid services, no cloud accounts, no external dependencies beyond
a local MySQL instance.

Log in, browse ~38 seeded aircraft parts across 5 categories, search/filter/sort/paginate them,
add/edit/delete as an admin, watch a dashboard track inventory value and low-stock items in real
time, and see role-based access control, JWT auth with silent token refresh, and an audit trail
all working together.

## Table of contents

- [Technologies used](#technologies-used)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Getting started](#getting-started)
  - [Option A: Local setup](#option-a-local-setup)
  - [Option B: Docker](#option-b-docker)
- [Default login credentials](#default-login-credentials)
- [Configuration reference](#configuration-reference)
- [API reference](#api-reference)
- [Running the tests](#running-the-tests)
- [Project structure](#project-structure)
- [Security notes & known trade-offs](#security-notes--known-trade-offs)
- [Deviations from the original spec](#deviations-from-the-original-spec)
- [Smoke test walkthrough](#smoke-test-walkthrough)

## Technologies used

**Backend**

| Technology | Role |
|---|---|
| Java 17 | Language / runtime |
| Spring Boot 3.2 | Application framework |
| Spring MVC | REST controllers |
| Spring JDBC (`JdbcTemplate`) | Persistence: **no ORM/Hibernate**, hand-written SQL |
| Spring Security | Authentication + method-level authorization |
| `io.jsonwebtoken` (JJWT) | JWT signing/parsing (access + refresh tokens) |
| Bean Validation (Jakarta) | Request DTO validation |
| springdoc-openapi | Swagger UI / OpenAPI 3 docs |
| MySQL 8 | Database |
| Maven | Build tool |
| JUnit 5 + Mockito | Unit testing |

**Frontend**

| Technology | Role |
|---|---|
| Angular 12 | SPA framework |
| Angular Material | UI component library (table, dialogs, forms, paginator, toolbar) |
| Bootstrap 5 | Grid/layout utilities |
| RxJS | Async/reactive request handling, auth interceptor retry logic |
| TypeScript | Language |
| Angular CLI / npm | Build tooling |
| Jasmine + Karma | Unit testing |

**Infrastructure**

| Technology | Role |
|---|---|
| Docker + Docker Compose | Optional containerized run of MySQL + backend + frontend |
| nginx | Serves the production Angular build inside its container |

## Features

**Authentication & authorization**
- JWT-based login and registration, with password hashing via BCrypt
- Access tokens (1 hour) + refresh tokens (7 days); the frontend silently renews an expired
  access token before it ever shows the user a login screen, and recovers transparently from a
  token going bad mid-session
- Role-based access control (`ADMIN` / `USER`) enforced on the backend via method-level
  `@PreAuthorize`, mirrored in the UI (non-admins never see controls they can't use)
- Self-service registration that can never create an `ADMIN` account, even via a direct API call

**Product inventory management**
- Full CRUD on aircraft parts (name, category, quantity, unit price, supplier, reorder level)
- Server-side pagination, name search, category filtering, and column sorting
- Low-stock tracking: a dedicated view flags parts at or below their reorder point
- Audit trail: every part records who created and last updated it
- Bean-validated input with clean, structured error responses (no stack traces ever reach the
  client)

**Dashboard**
- Live stat tiles: total products, total inventory value, low-stock count, category count
- Clicking the low-stock tile jumps straight to the low-stock view

**Developer experience**
- Interactive API docs via Swagger UI, with JWT auth wired in so you can call protected endpoints
  straight from the browser
- Unit test coverage on both sides (28 backend, 21 frontend)
- One-command Docker Compose stack as an alternative to the local toolchain
- Every non-obvious implementation choice is documented inline and in this README, not just
  assumed obvious

## Architecture

```
                        ┌─────────────────────────────┐
  Browser  ───────────► │   Angular 12 SPA (:4200)    │
                        │   LoginComponent, Register, │
                        │   ProductListComponent, ... │
                        └──────────────┬──────────────┘
                                       │ HttpClient + AuthInterceptor
                                       │ (attaches JWT, retries on 401)
                                       ▼
                        ┌─────────────────────────────┐
                        │  Spring Boot API (:8080)    │
                        │  JwtAuthFilter → Controller │
                        │  → Service → Repository     │
                        │  (JdbcTemplate)             │
                        └──────────────┬──────────────┘
                                       │ JDBC
                                       ▼
                        ┌─────────────────────────────┐
                        │       MySQL 8 (:3306)       │
                        │  products, users tables     │
                        └─────────────────────────────┘
```

**Backend** is a classic layered Spring MVC application: `Controller → Service → Repository →
MySQL`. There is deliberately **no ORM/Hibernate**; persistence is done with `JdbcTemplate` and
hand-written SQL, with `schema.sql`/`data.sql` creating and seeding the database on every
startup.

**Auth** is stateless JWT. `/api/auth/login`, `/api/auth/register`, and `/api/auth/refresh` are
the only public endpoints; everything else requires `Authorization: Bearer <token>`, validated by
a custom `OncePerRequestFilter` ahead of Spring Security's filter chain.

**Authorization** layers role-based access on top of authentication: both roles can read
products, but create/update/delete require `ADMIN`, enforced via
`@PreAuthorize("hasRole('ADMIN')")` at the service layer (`@EnableMethodSecurity`), not just in
the URL routing, so the rule holds for any future caller of those methods. A `USER` token gets a
clean 403 from the same global exception handler that handles every other error, not a stack
trace. The frontend mirrors this by hiding controls a non-admin can't use, rather than showing
them and letting the request fail.

**Global error handling**: a single `@RestControllerAdvice` (`GlobalExceptionHandler`) handles
validation failures, not-found, bad credentials, invalid tokens, access-denied, and unhandled
exceptions, always returning the same `{status, message, timestamp}` JSON shape.

**Frontend** talks to the backend via an absolute URL (`http://localhost:8080/api`), not Angular
CLI's dev-server proxy; see [Deviations](#deviations-from-the-original-spec) for why. `AuthGuard`
protects routes and silently refreshes an expired token before redirecting to login; `AuthGuard`
and `AuthInterceptor` share one `AuthService.refreshToken()` call for two different triggers
(pre-navigation vs. mid-session 401).

## Prerequisites

| Tool | Version used in this build | Notes |
|---|---|---|
| Java (JDK) | 17 | `brew install openjdk@17` |
| Maven | 3.9.x | `brew install maven` |
| MySQL | 8.0 | `brew install mysql@8.0` (skip if using Docker) |
| Node.js | 18.x | see the Node version caveat below (skip if using Docker) |
| npm | 9.x/10.x | ships with Node |
| Angular CLI | 12.x | invoked via `npx`, not required globally |
| Docker + Docker Compose | any recent version | only needed for [Option B](#option-b-docker) |

**Node version caveat:** Angular 12 (2021) predates Node's move to OpenSSL 3 (Node 17+). On
Node 18+ its webpack build throws `error:0308010C:digital envelope routines::unsupported` unless
you either use Node 16 or set `NODE_OPTIONS=--openssl-legacy-provider`. This project's
`npm start`/`npm run build` scripts set that flag automatically, so you don't need to remember
it for local development. The Docker build sidesteps this entirely by pinning Node 16 inside the
container. See [Deviations](#deviations-from-the-original-spec) for the full explanation.

## Getting started

You have two options: run everything locally with Homebrew/Maven/npm (closer to normal
day-to-day development, faster rebuilds), or run everything in Docker (one command, no local
toolchain required beyond Docker itself). Don't run both at once: they'd fight over the same
ports (8080, 4200, 3306).

### Option A: Local setup

**1. Start MySQL and create the database:**

```bash
brew services start mysql@8.0
```

```bash
/opt/homebrew/opt/mysql@8.0/bin/mysql -u root <<'EOF'
CREATE DATABASE IF NOT EXISTS airbus_inventory;
CREATE USER IF NOT EXISTS 'airbus_app'@'localhost' IDENTIFIED BY 'airbus_app_pw';
GRANT ALL PRIVILEGES ON airbus_inventory.* TO 'airbus_app'@'localhost';
FLUSH PRIVILEGES;
EOF
```

That's the only manual database step. Tables and seed data (~38 aircraft parts, 2 demo users)
are created automatically by Spring Boot on every backend startup via `schema.sql`/`data.sql`.
Tables are dropped and recreated on each restart so the demo always boots into the same known
state; see [Security notes & known trade-offs](#security-notes--known-trade-offs) for why that's
intentional. If you use different MySQL credentials, override them via environment variables
(see [Configuration reference](#configuration-reference)) instead of editing
`application.properties`.

**2. Start the backend:**

```bash
cd airbus-inventory-backend
mvn spring-boot:run
```

Starts on **http://localhost:8080**.

**3. Start the frontend** (in a second terminal):

```bash
cd airbus-inventory-frontend
npm install
npm start
```

Starts on **http://localhost:4200**. Open it in your browser and log in: see
[Default login credentials](#default-login-credentials).

### Option B: Docker

Bundles MySQL, the backend, and an nginx-served production build of the frontend into one
command:

```bash
docker compose up --build
```

Same ports as the local setup (frontend :4200, backend :8080, MySQL :3306); stop any
locally-running MySQL/backend/frontend first, since they'd conflict. `docker compose down` stops
everything; add `-v` to also drop the named MySQL volume and reset to a clean database on next
`up`.

Notes specific to the Docker build:
- The frontend's API URL (`http://localhost:8080/api`) is baked in at image build time. This
  works for local use because it's *your browser*, not the frontend container, that resolves
  `localhost:8080`; via Docker's port mapping, that reaches the backend container correctly. It
  would need a runtime-configurable URL (e.g. an nginx `envsubst` entrypoint) to work behind a
  different host or port mapping. Out of scope for this local demo.
- The frontend image is built with **Node 16**, not 18: inside a container we can pin the exact
  version Angular 12 officially supports, so the `NODE_OPTIONS` workaround the host setup needs
  isn't required there.

## Default login credentials

Seeded by `data.sql` on every backend startup, or create your own via the **Create an account**
link on the login page. Self-registration always creates a `USER`: the registration form has no
role field, and the backend hard-codes the role server-side, so no request (UI or direct API
call) can create an `ADMIN` account this way.

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | View + add/edit/delete products |
| `manager` | `manager123` | USER | View products only (read-only UI; backend returns 403 on any write) |

## Configuration reference

Everything below is overridable via environment variable; nothing is a hardcoded secret in
source. For anything beyond local demo use, set `JWT_SECRET` to your own base64-encoded random
value. Never commit a real secret to `application.properties`.

| Property | Env var | Default |
|---|---|---|
| DB URL | `DB_URL` | `jdbc:mysql://localhost:3306/airbus_inventory` |
| DB username | `DB_USERNAME` | `airbus_app` |
| DB password | `DB_PASSWORD` | `airbus_app_pw` |
| JWT secret (base64) | `JWT_SECRET` | a demo-only default baked into `application.properties` |
| JWT access token expiry (ms) | `JWT_EXPIRATION_MS` | `3600000` (1 hour) |
| JWT refresh token expiry (ms) | `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 days) |
| CORS allowed origin | `CORS_ALLOWED_ORIGIN` | `http://localhost:4200` |

## API reference

Full interactive docs (with a JWT "Authorize" button so you can call protected endpoints from the
browser): **http://localhost:8080/swagger-ui/index.html**, raw OpenAPI JSON at
`/v3/api-docs`. Both are public endpoints; nothing behind auth is needed to view the docs
themselves.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create a `USER` account, returns tokens |
| POST | `/api/auth/login` | Public | Authenticate, returns tokens |
| POST | `/api/auth/refresh` | Public | Exchange a refresh token for a new token pair |
| GET | `/api/products` | Any role | Paginated list, `?page=0&size=50` |
| GET | `/api/products/category/{category}` | Any role | Products in one category (unpaginated) |
| GET | `/api/products/low-stock` | Any role | Products at/below reorder level (unpaginated) |
| GET | `/api/products/summary` | Any role | Dashboard aggregate stats |
| GET | `/api/products/{id}` | Any role | Single product |
| POST | `/api/products` | `ADMIN` | Create a product |
| PUT | `/api/products/{id}` | `ADMIN` | Update a product |
| DELETE | `/api/products/{id}` | `ADMIN` | Delete a product |

All non-auth endpoints require `Authorization: Bearer <token>`. Every error response (validation,
404, 401, 403, 409, 500) shares the same JSON shape: `{status, message, timestamp}` (validation
errors also include a `details` array).

## Running the tests

**Backend**: JUnit 5 + Mockito unit tests for the service/security/exception layers
(`ProductService`, `AuthService`, `JwtUtil`, `GlobalExceptionHandler`). Deliberately scoped to
unit tests, not a DB-backed integration suite. Mocking the repositories keeps `mvn test` fast
and infra-independent (no MySQL required), at the cost of not exercising the real SQL; that's
covered instead by the manual smoke test below, which does hit a real database.

```bash
cd airbus-inventory-backend
mvn test
```

**Frontend**: Jasmine/Karma specs for `AuthService`, `ProductService` (both via
`HttpClientTestingModule`, no real backend needed) and `AuthGuard` (mocked `AuthService`/
`Router`, covering the fast-path/silent-refresh/redirect branches).

```bash
cd airbus-inventory-frontend
npm test                                              # interactive, watches for changes
npx ng test --browsers=ChromeHeadless --watch=false   # single run, e.g. for CI
```

Note: a single `--watch=false` run reliably prints a benign `Some of your tests did a full page
reload!` line *after* the real results, a known Angular 12/Karma artifact from a post-run
rebuild, not a failing spec. Trust the `TOTAL: N SUCCESS` line and the process exit code, not
that trailing line.

## Project structure

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

## Security notes & known trade-offs

Documented deliberately, not hidden: these are the kinds of things worth being able to explain
in an interview rather than pretend don't exist.

- **JWT stored in `localStorage`, not an `HttpOnly` cookie.** `AuthInterceptor` reads it from
  `localStorage` to attach it to every request, which means it's readable by any JS running on
  the page, so it's vulnerable to theft via XSS. The safer alternative (backend-set `HttpOnly`,
  `Secure`, `SameSite` cookies) would require the backend to also handle CSRF protection and
  cookie-based session semantics, which is more surface area than this demo's stated goal
  warranted. Same trade-off applies to the refresh token, stored the same way.
- **Refresh tokens are a second, longer-lived JWT** (`type: refresh` claim), not an opaque,
  revocable server-side token in a database table. Simpler and consistent with the rest of the
  app's stateless design, at the cost of not being revocable before it expires. A real
  production system would likely use opaque, DB-backed refresh tokens instead.
- **The interceptor's 401-retry is single-attempt and not de-duplicated** across concurrent
  requests: if several requests 401 around the same moment, each triggers its own `/refresh`
  call rather than one shared in-flight refresh the others await.
- **`schema.sql` drops and recreates tables on every backend startup**, so any product you add
  during a session resets on the next restart. This is intentional: it keeps `mvn spring-boot:run`
  idempotent and the demo deterministic, not a bug.
- **A privilege-escalation bug was found and fixed during development**: `/api/auth/register` is
  a public endpoint. It originally accepted an optional `role` field and trusted whatever the
  caller sent, meaning anyone could `curl` their way to an `ADMIN` account. Fixed by removing
  the `role` field from the registration DTO entirely (not just defaulting it), so self-registration
  can only ever produce a `USER`, regardless of what a request body contains. Worth calling out
  explicitly since it's exactly the kind of gap a security-conscious reviewer would look for.
- **Pagination's default page size (50) is larger than the seeded dataset (38 rows)** on purpose:
  the default experience is unchanged from before pagination existed (full-dataset search and
  sort still work), while genuine server-side `LIMIT`/`OFFSET` paging is exercised the moment you
  pick a smaller page size from the paginator. At a smaller page size, search/sort only apply
  within the currently-loaded page, not across all pages, a known, documented simplification.

## Deviations from the original spec

1. **Node 18 instead of Node matching Angular 12's official support window** (12.20-16.x): see
   the [Node version caveat](#prerequisites). Functionally transparent once
   `NODE_OPTIONS=--openssl-legacy-provider` is set; `npm start` sets it automatically. The Docker
   build avoids this deviation entirely by pinning Node 16.
2. **Frontend calls the backend via absolute URL, not the Angular CLI dev proxy**: a proxy would
   make requests same-origin from the browser's point of view, making the CORS requirement dead
   code. Absolute URLs mean the CORS configuration in `SecurityConfig` is actually exercised.
3. **`schema.sql` drops and recreates tables on every startup** rather than only on first run;
   see [Security notes & known trade-offs](#security-notes--known-trade-offs).
4. **Refresh tokens are a second JWT, not an opaque server-side token**; see
   [Security notes & known trade-offs](#security-notes--known-trade-offs).
5. **Backend unit tests only, no DB-backed integration suite**; see
   [Running the tests](#running-the-tests).
6. Everything else (Spring Boot 3.x, Spring MVC, Spring JDBC/`JdbcTemplate`, MySQL 8, JWT via
   `io.jsonwebtoken`, Bean Validation, `@RestControllerAdvice`, Angular 12, Angular Material,
   Bootstrap, Maven, Angular CLI) matches the requested stack exactly.

## Smoke test walkthrough

With MySQL running and both apps started (backend on 8080, frontend on 4200):

### Via the UI

1. Open http://localhost:4200. You're redirected to `/login` because there's no token yet
   (`AuthGuard`).
2. Click **Create an account**, register a new user, mismatch the password/confirm fields first
   to see the validation message, then fix it and submit: you're auto-logged-in and land on
   `/products` as a plain `USER` (no "Add Product" button, no Actions column).
3. Log out, log back in with `admin` / `admin123`. Above the filter bar you'll see four stat
   tiles (Total Products, Inventory Value, Low Stock Items, Categories) from
   `GET /api/products/summary`.
4. Try the name search box, the category filter, and column sorting on the product table
   (~38 seeded parts).
5. Click **Add Product**, fill in the form, submit: it appears in the table, and the stat tiles
   refresh.
6. Click the edit icon on a row. The dialog header shows "Added by ... / last updated by ..."
   (audit fields); change a field, save: the row and the audit line update.
7. Click the delete icon. A confirmation dialog appears; confirm and the row disappears.
8. Click the **Low Stock Items** tile (or flip the **Low stock only** toggle directly): the
   table narrows to the parts seeded at/below their reorder level (Main Landing Gear Strut
   Assembly, Combustion Chamber Liner, Turbine Disc); the category filter disables itself while
   the toggle is on.
9. Turn the toggle back off, then use the paginator at the bottom of the table: switch the page
   size to 10 and page through; open your browser's network tab and confirm each page click
   issues a new `GET /api/products?page=...&size=10` request rather than re-slicing data already
   in memory.
10. Click **Logout**, then log back in as `manager` / `manager123`: same read-only view as
    registration produced; the low-stock toggle and pagination still work (reading is allowed
    for both roles).
11. Click **Logout** again, then try navigating back to `/products` directly: you're bounced
    back to `/login` because the token was cleared.

**Refresh-token check (optional, needs devtools):** while logged in, open your browser's
Application/Storage tab, find `airbus_auth_token` in `localStorage`, and replace its value with
any string ending the same way but with a corrupted signature (e.g. append a character). Trigger
any request (switch the category filter). The app doesn't break: `AuthInterceptor` catches the
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
# smuggle one in gets silently dropped by Jackson. This MUST come back "role":"USER"
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

# 4. Full CRUD (products list is now paginated: page/size params, {content, totalElements, ...})
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

All of the above was run against this exact build during development.
