# Spring Boot Java Starter

A production-leaning backend starter built with Spring Boot and Java. It ships a complete, stateless **JWT authentication** flow and **role-based user management** on top of a clean, layered structure, so you can start building product features instead of wiring auth from scratch.

## What's included

- **Auth** — register, login, refresh, logout, logout-all, profile (JWT access + refresh tokens)
- **Refresh-token rotation** — single-use refresh tokens with server-side tracking and reuse detection (a replayed token nukes the whole session family)
- **Rate limiting** — per-IP token-bucket throttling on `login` / `register` (Bucket4j), returns `429` + `Retry-After`
- **User management** — admin-only list (paginated + sortable), get by id, change role, soft delete
- **Role-based access control** — `USER` / `ADMIN`, enforced with `@PreAuthorize` and a stateless JWT filter
- **Security hardening** — BCrypt hashing, stateless sessions, CORS allowlist (no wildcards), fail-fast startup checks on weak/missing config
- **Consistent error envelope** — one JSON shape for every error, with field-level validation details
- **Database-first migrations** — Flyway over PostgreSQL
- **API docs** — OpenAPI 3 + Swagger UI with a JWT "Authorize" button
- **Observability** — Spring Boot Actuator health/info endpoints (for liveness/readiness probes)
- **`.env` support** — local config via `spring-dotenv`

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.1 |
| Build | Gradle (Groovy DSL) |
| Database | PostgreSQL |
| ORM | Spring Data JPA (Hibernate) |
| Migrations | Flyway |
| Security | Spring Security + JJWT 0.12.6 |
| Rate limiting | Bucket4j 8.19 (in-memory) |
| Docs | springdoc-openapi 2.8.0 |

## Requirements

- JDK 21+
- PostgreSQL 12+ (for running the app; the test suite uses in-memory H2 and needs no database)

## Quick start

### 1) Create the database

```sql
CREATE DATABASE starter_db;
CREATE USER starter_user WITH ENCRYPTED PASSWORD 'starter_pass';
GRANT ALL PRIVILEGES ON DATABASE starter_db TO starter_user;
```

### 2) Create a `.env` file

Copy `.env.example` to `.env` and fill it in. The important fields:

```env
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8090

DB_URL=jdbc:postgresql://localhost:5432/starter_db
DB_USERNAME=starter_user
DB_PASSWORD=starter_pass

# Must be at least 32 characters (256-bit) or the app refuses to start.
# Generate one with: openssl rand -base64 48
JWT_SECRET=replace-me-with-a-strong-secret-at-least-32-characters-long
JWT_ACCESS_EXP_MINUTES=15
JWT_REFRESH_EXP_DAYS=7

# Local/dev only: seed an initial admin on startup.
ADMIN_SEED_ENABLED=false
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=admin@pass123

CORS_ALLOWED_ORIGINS=http://localhost:3000

# Rate limiting on /auth/login and /auth/register (per client IP).
RATE_LIMIT_ENABLED=true
RATE_LIMIT_CAPACITY=5
RATE_LIMIT_WINDOW_SECONDS=60
```

`.env` is git-ignored. For dev/staging/prod, use your platform's secret manager and set the same environment variables.

### 3) Run

Need a local Postgres fast? Use the bundled compose file (its credentials match the `local` profile defaults, so no `.env` DB tweaks needed):

```bash
docker compose up -d            # Postgres on :5432, Adminer on :8081
./gradlew bootRun --args='--spring.profiles.active=local'
```

Flyway applies `src/main/resources/db/migration` on startup, so the schema is created automatically.

To containerize the app itself, a multi-stage `Dockerfile` is included (`docker build -t starter .`).

### 4) Try it

- Swagger UI: `http://localhost:8090/swagger-ui.html`
- Health: `http://localhost:8090/actuator/health`

```bash
# Register (returns access + refresh tokens)
curl -X POST http://localhost:8090/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"password123"}'

# Call a protected endpoint
curl http://localhost:8090/api/v1/auth/profile \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

## API

Base path: `/api/v1`

### Auth (`/auth`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/register` | public | Create a `USER` account, returns tokens |
| POST | `/login` | public | Authenticate, returns tokens |
| POST | `/refresh` | public | Exchange a refresh token for new tokens |
| POST | `/logout` | public | Client-side token discard (stateless) |
| POST | `/logout-all` | authenticated | Invalidate every session for the caller (bumps token version) |
| GET | `/profile` | authenticated | Current user's profile |

### Users (`/users`) — `ADMIN` only

| Method | Path | Description |
|---|---|---|
| GET | `/` | Paginated list (`page`, `size`, `sort=field,dir`) |
| GET | `/{id}` | Get a user by id |
| PATCH | `/{id}/role` | Change a user's role |
| DELETE | `/{id}` | Soft-delete a user |

Allowed sort fields: `createdAt`, `updatedAt`, `email`, `role`. Direction: `asc` / `desc`.

## How auth works

- **Stateless JWTs.** Login/register/refresh issue a short-lived **access** token and a longer-lived **refresh** token, both signed with `JWT_SECRET` (HS256).
- **Authority is read from the database, never from the token.** The JWT filter loads the user on every request and derives the role from `user.role`. A role change or demotion takes effect immediately; a stale `roles` claim in an already-issued token can never grant elevated access.
- **Token versioning for revocation.** Each user has a `tokenVersion`. `logout-all`, a role change, and a soft-delete all increment it, which instantly invalidates any token issued before the change.
- **Soft delete.** Users are disabled (`disabled_at`), not removed. Disabled users fail authentication immediately.
- **Admin safety guards.** An admin cannot change their own role or delete their own account, and the system refuses to demote or delete the last remaining admin (no lock-out).
- **Refresh-token rotation.** Each refresh token carries a `jti` tracked in `refresh_tokens`. Calling `/refresh` revokes the presented token and issues a new one (single-use). Replaying an already-rotated token is treated as theft: the user's whole refresh-token family is revoked and `tokenVersion` is bumped, forcing every session to re-authenticate. Expired rows are purged by a daily scheduled job.
- **Rate limiting.** `login` and `register` are throttled per client IP (`capacity` requests per `window-seconds`, default 5/60s). Over-limit requests get `429` with a `Retry-After` header. In-memory and single-node by default; back Bucket4j with Redis/Hazelcast for multi-instance.

## Error format

Every error returns the same envelope:

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "details": [
    { "field": "password", "message": "Password must be between 8 and 30 characters" }
  ]
}
```

## Configuration & profiles

- `application.yml` — shared defaults
- `application-{local,dev,staging,prod}.yml` — per-environment overrides

`local` ships datasource defaults and the admin-seed toggle for convenience; `dev`/`staging`/`prod` require all secrets to be supplied via the environment.

Startup fails fast (refuses to boot) when:
- `JWT_SECRET` is missing or shorter than 32 characters
- `CORS_ALLOWED_ORIGINS` is empty or contains `*`

## Build & test

```bash
./gradlew clean build      # compile + run all tests
./gradlew test             # tests only
./gradlew bootRun          # run (set the profile via --args)
```

The test suite runs entirely on in-memory H2 (`test` profile) with Flyway disabled, so `./gradlew test` is green without any database or Docker.

## Project layout

```text
src/main/java/com/company/starter
├── StarterApplication.java
├── auth/            # controller, service, DTOs
├── user/            # controller, service, model, repository, seeder
├── security/        # SecurityConfig, JWT filter/service, handlers, SecurityUtils
├── common/          # error envelope + exceptions, pagination
└── config/          # AppProperties, CORS, OpenAPI
```

## Suggested next steps

This starter ships a clean, secure baseline with JWT auth, RBAC, refresh-token rotation, and rate limiting. Common additions:

- **Distributed rate limiting** — back Bucket4j with Redis/Hazelcast (`ProxyManager`) for multi-node deployments
- **Email verification / password reset** flows
- **Audit logging** for auth-sensitive operations
- **Metrics & tracing** (Micrometer + OpenTelemetry)

## License

Add a license that matches your intended usage (e.g. MIT, Apache-2.0, proprietary).
