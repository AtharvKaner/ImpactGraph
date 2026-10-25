# ImpactGraph

Software dependency and change-impact analysis platform.

## Module: Security, Authentication, Authorization, Audit Logging & Data Transparency

This module provides:
- **JWT authentication** — stateless, HS512-signed tokens via Spring Security
- **Role-based authorization** — `ADMIN` and `DEVELOPER` roles
- **Project membership enforcement** — developers can only access their assigned projects (IDOR prevention)
- **Persistent audit logging** — 14 event types, immutable via API, never logs credentials
- **Data transparency** — public data-collection disclosure and per-user activity feed
- **Admin provisioning** — environment-driven bootstrap for the initial admin account

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 3.2.5, Spring Security, Spring Data JPA |
| Database | PostgreSQL (production), H2 (tests) |
| Auth | JWT (jjwt 0.12.5), BCrypt (cost 12) |
| Frontend | React + Vite (data-transparency dashboard) |
| Build | Maven (with wrapper) |

## Quick Start

### Prerequisites
- Java 21+
- PostgreSQL 14+ (or use H2 for testing only)
- Node.js 18+ and npm (for the frontend)

### 1. Database Setup (Optional)
If you have Docker installed, you can start a PostgreSQL container using the included `docker-compose.yml`:
```powershell
docker compose up -d
```
*(If PostgreSQL is not running, the application seamlessly uses the `dev` profile with an embedded H2 database).*

### 2. Start the Backend

**Option A — Automated Development Runner (Recommended):**
```powershell
cd "backend"
.\run-backend.ps1
```

**Option B — Manual Startup:**
```powershell
cd "backend"
# Set 512-bit JWT secret in session memory
$bytes = New-Object byte[] 64
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()

# If PostgreSQL is not running, activate the dev profile (embedded H2)
$env:SPRING_PROFILES_ACTIVE = "dev"

.\mvnw.cmd spring-boot:run
```
The API is available at `http://localhost:8080`.

### 3. Start the Frontend

```powershell
cd "frontend"
npm.cmd install
npm.cmd run dev
```
The dashboard is available at `http://localhost:5173` (proxies `/api` calls to `http://localhost:8080`).

### 4. Run Automated Tests

```powershell
cd "backend"
.\mvnw.cmd clean test
```
Tests run fully isolated in memory using H2 with 100% pass rate (35 of 35 tests).

## API Endpoints

### Public (no auth)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health check |
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive JWT |
| GET | `/api/transparency/public` | Data transparency info |

### Authenticated
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/projects` | List accessible projects |
| GET | `/api/projects/{id}` | Get project details (membership enforced) |
| GET | `/api/projects/{id}/members` | List project members |
| GET | `/api/transparency/my-activity` | User's own audit events |

### Admin Only
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/projects` | Create a project |
| PUT | `/api/projects/{id}` | Update a project |
| DELETE | `/api/projects/{id}` | Delete a project |
| POST | `/api/admin/memberships` | Grant project membership |
| DELETE | `/api/admin/memberships` | Revoke project membership |
| GET | `/api/audit/events` | List audit events (paginated) |
| GET | `/api/audit/events/by-project/{id}` | Audit events by project |
| GET | `/api/audit/events/by-type/{type}` | Audit events by type |

## Security Design Decisions

1. **Passwords** are BCrypt-hashed (cost 12) and never logged or returned in API responses.
2. **Login errors** return a generic message — never reveal whether an email exists.
3. **JWT tokens** are signed with HS512 and carry userId, email, and role claims.
4. **CSRF is disabled** because authentication is stateless (JWT in Authorization header, no cookies).
5. **Audit events** use `REQUIRES_NEW` propagation so they persist even if the business transaction rolls back.
6. **Audit records** are immutable via the API — no update or delete endpoints exist.
7. **Cross-project access** is prevented: developers can only see projects they are members of.
8. **The first registered user** automatically receives the `ADMIN` role if no admin exists.

## Project Structure

```
backend/src/main/java/com/impactgraph/
├── ImpactGraphApplication.java       # Spring Boot entry point
├── controller/
│   ├── AuthController.java           # Login & registration
│   ├── ProjectController.java        # Project CRUD
│   ├── AdminController.java          # Membership management
│   ├── AuditController.java          # Audit log viewing
│   ├── TransparencyController.java   # Data transparency
│   ├── HealthController.java         # Health check
│   └── GlobalExceptionHandler.java   # Error handling
├── dto/                              # Request/response objects
├── entity/                           # JPA entities
├── repository/                       # Spring Data repositories
├── security/                         # JWT, filters, config
└── service/                          # Business logic
```

## Integration Notes

The `AuditEventType` enum includes `DEPENDENCY_CREATED`, `DEPENDENCY_UPDATED`, and `DEPENDENCY_DELETED` for future integration with the core dependency-graph module. The dependency entity, service, and controller are owned by the core backend team.
