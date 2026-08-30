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

## Default demo login credentials

Seeded by `data.sql` on every backend startup:

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | View + add/edit/delete products |
| `manager` | `manager123` | USER | View products only (read-only UI, backend 403s on writes) |

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
4. Everything else (Spring Boot 3.x, Spring MVC, Spring JDBC/`JdbcTemplate`, MySQL 8, JWT via
   `io.jsonwebtoken`, Bean Validation, `@RestControllerAdvice`, Angular 12, Angular Material,
   Bootstrap, Maven, Angular CLI) matches the requested stack exactly.

## Smoke test

With MySQL running and both apps started (backend on 8080, frontend on 4200):

### Via the UI
1. Open http://localhost:4200 — you're redirected to `/login` because there's no token yet
   (`AuthGuard`).
2. Log in with `admin` / `admin123`.
3. You land on the product table (~38 seeded parts). Try the name search box, the category
   filter, and column sorting.
4. Click **Add Product**, fill in the form, submit — it appears in the table.
5. Click the edit icon on a row, change a field, save — the row updates.
6. Click the delete icon — a confirmation dialog appears; confirm and the row disappears.
7. Flip the **Low stock only** toggle — the table narrows to the parts seeded at/below their
   reorder level (Main Landing Gear Strut Assembly, Combustion Chamber Liner, Turbine Disc); the
   category filter disables itself while the toggle is on.
8. Click **Logout**, then log back in as `manager` / `manager123` — notice there's no "Add
   Product" button and no Actions column at all; the low-stock toggle still works (reading is
   allowed for both roles).
9. Click **Logout** again, then try navigating back to `/products` directly — you're bounced
   back to `/login` because the token was cleared.

### Via curl (backend only)
```bash
# 1. Protected route rejects an unauthenticated request
curl -i http://localhost:8080/api/products
# -> 401, clean JSON error body (no stack trace)

# 2. Register a new user
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_engineer","password":"demoPass1"}'

# 3. Log in as the seeded admin and capture the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# 4. Full CRUD
curl -s http://localhost:8080/api/products -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8080/api/products/category/Hydraulics" -H "Authorization: Bearer $TOKEN"
NEW_ID=$(curl -s -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Test Sensor","category":"Avionics","quantity":10,"unitPrice":100.50,"supplier":"Test Supplier","reorderLevel":2}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
curl -s http://localhost:8080/api/products/$NEW_ID -H "Authorization: Bearer $TOKEN"
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
```

All of the above was run against this exact build during development — see the commit/PR
description for captured output.

## Project layout

```
Airbus/
  README.md                        (this file)
  airbus-inventory-backend/
    pom.xml
    src/main/java/com/airbus/inventory/
      controller/    AuthController, ProductController
      service/       AuthService, ProductService
      repository/    UserRepository, ProductRepository (JdbcTemplate)
      security/      JwtUtil, JwtAuthFilter, UserDetailsServiceImpl, entry point / access denied handler
      config/        SecurityConfig (filter chain, CORS, password encoder)
      dto/           request/response DTOs with Bean Validation
      exception/     GlobalExceptionHandler + custom exceptions
      model/         Product, User
    src/main/resources/
      application.properties
      schema.sql
      data.sql
  airbus-inventory-frontend/
    src/app/
      components/    login, product-list, product-form (dialog), confirm-dialog
      services/      AuthService, ProductService
      guards/        AuthGuard
      interceptors/  AuthInterceptor
      models/        Product, auth, error-response
```
