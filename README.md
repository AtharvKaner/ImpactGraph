# ImpactGraph

ImpactGraph is an academic Software Change Impact Analysis platform with built-in CNS security features (authentication, authorization, audit logging, and data-flow transparency).

This repository contains **Milestone 1**: the initial runnable skeleton featuring a Spring Boot backend and React/Vite frontend connected via a health-check endpoint.

---

## Project Structure

```text
ImpactGraph/
├── backend/                       # Spring Boot backend application
│   ├── .mvn/wrapper/              # Maven Wrapper files
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/impactgraph/
│   │   │   │   ├── ImpactGraphApplication.java # Spring Boot entry point
│   │   │   │   ├── config/WebConfig.java       # CORS configuration
│   │   │   │   ├── controller/                 # REST Controllers (Health, Project, Service, Api, Dependency)
│   │   │   │   ├── dto/                        # Request and Response DTOs
│   │   │   │   ├── entity/                     # Domain Entities (Project, Service, Api, Dependency)
│   │   │   │   ├── exception/                  # Global Exception Handling & Custom Exceptions
│   │   │   │   ├── graph/                      # DependencyGraph, CycleDetector, ImpactAnalyzer
│   │   │   │   ├── repository/                 # Spring Data Repositories
│   │   │   │   └── service/                    # Business Service Layer & Graph Bridge
│   │   │   └── resources/
│   │   │       └── application.properties      # PostgreSQL config via env vars
│   │   └── test/
│   │       ├── java/com/impactgraph/           # Unit, Integration & Compatibility Tests
│   │       └── resources/
│   │           └── application.properties      # In-memory test DB config
│   ├── .env.example               # Backend environment variable template
│   ├── .gitignore                 # Backend-specific ignore rules
│   ├── mvnw / mvnw.cmd            # Maven Wrapper scripts
│   └── pom.xml                    # Maven configuration & dependencies
│
├── frontend/                      # React / Vite frontend application
│   ├── src/
│   │   ├── App.jsx                # "ImpactGraph" status & health check UI
│   │   ├── App.css                # Component styling
│   │   ├── index.css              # Global styles
│   │   └── main.jsx               # React DOM entry point
│   ├── index.html                 # Main HTML document titled "ImpactGraph"
│   ├── package.json               # Frontend dependencies (React, Vite, @xyflow/react)
│   ├── vite.config.js             # Vite configuration with /api proxy
│   ├── .env.example               # Frontend environment variable template
│   └── .gitignore                 # Frontend-specific ignore rules
│
├── .env.example                   # Root environment variable template
├── .gitignore                     # Root repository ignore rules
└── README.md                      # Project documentation & run guide
```

---

## Tech Stack (Milestone 1)

- **Backend:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL Driver, Maven Wrapper
- **Frontend:** React 19, Vite, React Flow (`@xyflow/react`)
- **Testing:** JUnit 5, Mockito, Spring Boot Test, H2 (for isolated test execution)
- **Tooling:** Maven Wrapper (`mvnw.cmd`), npm, oxlint

---

## Prerequisites

Before running the project on Windows, verify that the following prerequisites are installed:

1. **Java Development Kit (JDK) 21**
   ```powershell
   java -version
   ```
   *Expected: `java version "21.x.x"`*

2. **Node.js (v18+ or v20+/v22+) and npm**
   ```powershell
   node -v
   npm -v
   ```

3. **PostgreSQL (Optional for tests, required for runtime)**
   - PostgreSQL 14+ running locally on port `5432` with a database named `impactgraph`.
   - *Note: Unit and integration tests (`mvn test`) run using an embedded in-memory database and do NOT require PostgreSQL to be installed or running.*

---

## Environment Variables

Database credentials, secrets, and integration keys are **strictly externalized** and never hardcoded in repository files.

| Variable Name | Default Value | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `impactgraph` | PostgreSQL database name (default: `impactgraph`) |
| `DB_USERNAME` | `postgres` | Database username (default: `postgres`) |
| `DB_PASSWORD` | *(none / empty)* | **Required** for local PostgreSQL if SCRAM-SHA-256 auth is enabled |
| `SPRING_DATASOURCE_PASSWORD` | *(falls back to DB_PASSWORD)* | Alternative Spring Boot datasource password variable |
| `SPRING_DATASOURCE_URL` | *(derived from DB_HOST/PORT/NAME)* | Full JDBC URL override (e.g. `jdbc:postgresql://localhost:5432/impactgraph`) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | JPA schema DDL mode (`update`, `validate`, `none`) |
| `PORT` | `8080` | Backend HTTP server port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Allowed frontend origins |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Frontend backend API target |
| `JWT_SECRET` | *(none / empty)* | **Required** for production: HMAC-SHA512 signing key (minimum 64 characters) |
| `JWT_EXPIRATION_MS` | `86400000` | JWT token validity window (defaults to 24 hours in ms) |
| `ADMIN_BOOTSTRAP_EMAIL` | *(none / empty)* | Initial administrator email to provision on first startup |
| `ADMIN_BOOTSTRAP_PASSWORD` | *(none / empty)* | Initial administrator password (must meet complexity requirements) |
| `GITHUB_WEBHOOK_SECRET` | *(none / empty)* | Secret for HMAC-SHA256 GitHub pull request webhook signature verification |
| `AI_PROVIDER` | `deterministic` | Offline deterministic security explanation engine |

Templates are provided in `.env.example` (root) and `backend/.env.example`.

Spring Boot automatically supports loading local `.env` files via `spring.config.import=optional:file:.env[.properties],optional:file:../.env[.properties]`. All `.env` files are strictly excluded in `.gitignore`.

---

## Exact Windows Execution Commands

Open two PowerShell terminals: one for the backend and one for the frontend.

### Terminal 1: Backend (Spring Boot)

1. **Navigate to the backend directory:**
   ```powershell
   cd backend
   ```

2. **Run backend automated tests (JUnit + Mockito with in-memory H2):**
   ```powershell
   .\mvnw.cmd clean test
   ```
   *Note: Tests run completely in-memory and do not require PostgreSQL or external credentials.*

3. **Configure PostgreSQL credentials before running the application:**

   If your local PostgreSQL server uses SCRAM authentication, providing no password will trigger:
   `The server requested SCRAM-based authentication, but no password was provided.`

   Choose **either** of the two safe approaches below:

   **Option A: Set session environment variable in PowerShell (Recommended)**
   ```powershell
   $env:DB_PASSWORD = "your_actual_postgres_password"
   ```
   *(Optionally override defaults if your database name, user, or port differ:)*
   ```powershell
   $env:DB_NAME = "impactgraph"
   $env:DB_USERNAME = "postgres"
   $env:DB_HOST = "localhost"
   $env:DB_PORT = "5432"
   ```

   **Option B: Use a gitignored local `.env` file**
   ```powershell
   # From the backend directory:
   Copy-Item .env.example .env
   # Or from the project root:
   # Copy-Item ..\.env.example ..\.env
   ```
   Open the created `.env` file and set your local password:
   ```properties
   DB_PASSWORD=your_actual_postgres_password
   ```
   *(Spring Boot automatically reads `.env` on startup; `.env` is gitignored so credentials will never be committed.)*

4. **Start the backend application:**
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   *Backend starts at `http://localhost:8080` and connects to PostgreSQL using the configured credentials.*

   **For the ready-to-use local demo (recommended for coursework):**
   ```powershell
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```
   This uses an in-memory H2 database and creates the local demo account:
   `demo@impactgraph.io` / `DemoPassword123!`.
   The account and demo topology exist only while that backend process is running.
   The demo seeder is disabled by default outside the `dev` profile, so a normal
   PostgreSQL or production startup does not create a known account.

5. **Verify the health check directly in your browser or via curl:**
   ```powershell
   curl.exe http://localhost:8080/api/health
   ```
   *Expected Response:*
   ```json
   {
     "status": "UP",
     "service": "ImpactGraph Backend",
     "timestamp": "2026-10-24T12:00:00.123456Z"
   }
   ```

---

### Troubleshooting Common Startup Issues

- **`The server requested SCRAM-based authentication, but no password was provided.`**
  - **Cause:** PostgreSQL requires password authentication for user `postgres`, but `$env:DB_PASSWORD` was not set in your current terminal session and no `.env` file was populated.
  - **Resolution:** Set `$env:DB_PASSWORD = "your_password"` in your active PowerShell window, or populate `DB_PASSWORD=your_password` in `backend/.env`, then rerun `.\mvnw.cmd spring-boot:run`.

- **`FATAL: database "impactgraph" does not exist`**
  - **Cause:** PostgreSQL is running, but the database has not been created yet.
  - **Resolution:** Create the database using PostgreSQL CLI:
    ```powershell
    psql -U postgres -c "CREATE DATABASE impactgraph;"
    ```

---

### Terminal 2: Frontend (React + Vite)

1. **Navigate to the frontend directory:**
   ```powershell
   cd frontend
   ```

2. **Install dependencies:**
   ```powershell
   npm install
   ```

3. **Run linter:**
   ```powershell
   npm run lint
   ```

4. **Run production build:**
   ```powershell
   npm run build
   ```

5. **Start the frontend development server:**
   ```powershell
   npm run dev
   ```
   *Frontend starts at `http://localhost:5173`.*

6. **Open the application:**
   Open [http://localhost:5173](http://localhost:5173) in your browser. The page titled **ImpactGraph** will query `http://localhost:8080/api/health` and display a live status badge indicating whether the backend service is reachable.

---

## Core Data Model & REST Endpoints

The backend exposes a fully validated CRUD API for modeling architectural entities. JPA domain models are fully decoupled from REST DTOs.

### Direction Convention
> **Important:** Directed dependency edges follow the standard graph convention:
> **`A -> B` means Service A depends on Service B** (Service A is the dependent, Service B is the dependency).

### Endpoints Overview

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Service health status check |
| `POST` | `/api/projects` | Create a new project |
| `GET` | `/api/projects` | List all projects |
| `GET` | `/api/projects/{id}` | Get project by ID |
| `PUT` | `/api/projects/{id}` | Update project |
| `DELETE` | `/api/projects/{id}` | Delete project (cascades dependencies & services) |
| `GET` | `/api/projects/{id}/services` | List all services belonging to a project |
| `GET` | `/api/projects/{id}/dependencies` | List all dependencies belonging to a project |
| `POST` | `/api/services` | Create a service under a project |
| `GET` | `/api/services` | List services (optional `?projectId={id}` filter) |
| `GET` | `/api/services/{id}` | Get service by ID |
| `PUT` | `/api/services/{id}` | Update service |
| `DELETE` | `/api/services/{id}` | Delete service (cascades related dependencies & APIs) |
| `GET` | `/api/services/{id}/apis` | List all APIs exposed by a service |
| `POST` | `/api/apis` | Create an API endpoint under a service |
| `GET` | `/api/apis` | List APIs (optional `?serviceId={id}` filter) |
| `GET` | `/api/apis/{id}` | Get API by ID |
| `PUT` | `/api/apis/{id}` | Update API |
| `DELETE` | `/api/apis/{id}` | Delete API |
| `POST` | `/api/dependencies` | Register a dependency between two services |
| `GET` | `/api/dependencies` | List dependencies (supports `?projectId=`, `?sourceServiceId=`, `?targetServiceId=`) |
| `GET` | `/api/dependencies/{id}` | Get dependency by ID |
| `PUT` | `/api/dependencies/{id}` | Update dependency |
| `DELETE` | `/api/dependencies/{id}` | Delete dependency |

### Example Payloads

#### 1. Create Project (`POST /api/projects`)
**Request:**
```json
{
  "name": "E-Commerce System",
  "description": "Retail microservices architecture"
}
```
**Response (`201 Created`):**
```json
{
  "id": 1,
  "name": "E-Commerce System",
  "description": "Retail microservices architecture",
  "createdAt": "2026-10-24T10:00:00Z",
  "serviceCount": 0
}
```

#### 2. Create Service (`POST /api/services`)
**Request:**
```json
{
  "projectId": 1,
  "name": "OrderService",
  "description": "Manages customer orders and checkout workflows"
}
```
**Response (`201 Created`):**
```json
{
  "id": 1,
  "projectId": 1,
  "projectName": "E-Commerce System",
  "name": "OrderService",
  "description": "Manages customer orders and checkout workflows",
  "apiCount": 0
}
```

#### 3. Create API (`POST /api/apis`)
**Request:**
```json
{
  "serviceId": 1,
  "name": "Create Order",
  "method": "POST",
  "path": "/orders",
  "description": "Places a new order"
}
```
**Response (`201 Created`):**
```json
{
  "id": 1,
  "serviceId": 1,
  "serviceName": "OrderService",
  "name": "Create Order",
  "method": "POST",
  "path": "/orders",
  "description": "Places a new order"
}
```

#### 4. Create Dependency (`POST /api/dependencies`)
> *Direction:* `OrderService` (source: 1) depends on `PaymentService` (target: 2).
**Request:**
```json
{
  "sourceServiceId": 1,
  "targetServiceId": 2,
  "dependencyType": "REST",
  "description": "OrderService calls PaymentService charge endpoint synchronously"
}
```
**Response (`201 Created`):**
```json
{
  "id": 1,
  "projectId": 1,
  "sourceServiceId": 1,
  "sourceServiceName": "OrderService",
  "targetServiceId": 2,
  "targetServiceName": "PaymentService",
  "dependencyType": "REST",
  "description": "OrderService calls PaymentService charge endpoint synchronously",
  "direction": "OrderService -> PaymentService"
}
```

### Data Integrity & Validation Rules
- **Self-Dependencies Rejected (`400 Bad Request`):** Source service and target service cannot be identical.
- **Duplicate Dependencies Rejected (`409 Conflict`):** Exactly one dependency relationship is permitted per `(sourceServiceId, targetServiceId)` pair.
- **Cross-Project Dependencies Rejected (`400 Bad Request`):** Services must belong to the exact same project to declare dependencies between them.
- **Parent Resource Validation (`404 Not Found`):** Services, APIs, and Dependencies must reference existing projects/services.
- **HTTP Method & Path Validation (`400 Bad Request`):** API methods must be valid (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`, `HEAD`) and paths must start with `/`.

---

## Security, AST Analysis & CI/CD Integrations

ImpactGraph features an enterprise-grade AST code analyzer and security rule engine (`JavaSourceAnalyzer`, `SecurityRuleEngine`) covering OWASP/CWE security rules:

| Rule ID | Severity | Category | Target / Description |
|---|---|---|---|
| `SEC-AUTH-001` | HIGH | Authentication | Overly permissive route (`permitAll()` on non-public endpoints) |
| `SEC-AUTHZ-002` | HIGH | Authorization | Missing `@PreAuthorize` or role enforcement on mutating/admin endpoints |
| `SEC-TAINT-003` | CRITICAL | Injection | SQL/HQL injection vulnerability via unparameterized string concatenation |
| `SEC-DATA-004` | MEDIUM | Data Protection | Hardcoded secret or credential literal detected in source code |
| `SEC-CONF-005` | MEDIUM | Configuration | Wildcard CORS origin (`*` with credentials or exposed endpoints) |

Endpoints exposed:
- `POST /api/security/analyze`: Triggers AST code analysis, rule evaluation, and targeted test selection across project source files.
- `GET /api/security/findings?projectId={id}`: Lists findings filtered by status or project with tenant isolation.
- `POST /api/security/findings/{id}/verify`: Re-evaluates live source code against finding rules, accurately verifying remediation or marking regression.
- `POST /api/security/ai-explanation`: Generates structured remediation guidance, risk summaries, and code diff suggestions.
- `GET /api/security/history?projectId={id}`: Lists historical analysis runs with statistical breakdown.
- `GET /api/security/inventory?projectId={id}`: Returns API inventory with authentication, authorization, and drift statuses.

---

## Database Setup, Constraints & Schema Evolution

### Entities & Constraints
The persistence layer manages relational entities: `Project`, `Service`, `Api`, `Dependency`, `AppUser`, `AuditEvent`, `TransparencyReport`, `SecurityFinding`, and `AnalysisRun`.

Key integrity constraints:
- `analysis_runs` Table:
  - Unique Constraint: `uk_runs_delivery_id` on `deliveryId VARCHAR(100)`.
  - ANSI SQL / PostgreSQL / H2 Semantics: `deliveryId` allows multiple `NULL` values (used by manual dashboard runs and test fixtures) while enforcing strict uniqueness on non-null values (GitHub webhook delivery UUIDs).
  - Performance Indexes: `idx_runs_timestamp`, `idx_runs_project`, `idx_runs_sha`.
- `dependencies` Table:
  - Unique Constraint: Unique `(source_service_id, target_service_id)` per project.
  - Foreign Keys: Cascading delete on services and projects.

### Production Schema Evolution & Migrations
In development and automated test profiles, Hibernate generates and updates schemas via `spring.jpa.hibernate.ddl-auto=update` or `create-drop`.

For production environments enforcing `spring.jpa.hibernate.ddl-auto=validate`, apply the following migration DDL:

```sql
-- Migration: Add delivery_id column and unique constraint to analysis_runs
ALTER TABLE analysis_runs
    ADD COLUMN IF NOT EXISTS delivery_id VARCHAR(100);

-- PostgreSQL partial unique index (or standard unique constraint):
CREATE UNIQUE INDEX IF NOT EXISTS uk_runs_delivery_id
    ON analysis_runs (delivery_id)
    WHERE delivery_id IS NOT NULL;
```

> **Database Safety Notice:** Automated tests run exclusively against an isolated in-memory H2 database (`jdbc:h2:mem:impactgraph_test`). No automated scripts connect to or modify external or production PostgreSQL databases. PostgreSQL schema migration verification on a live cluster must be performed within an isolated staging database.

---

## AI Configuration & Remediation Engine

ImpactGraph supports automated remediation explanations via the pluggable `AiExplanationService` interface:

1. **Deterministic Offline Engine (`AI_PROVIDER=deterministic`, default):**
   - Requires zero external network calls or third-party API keys.
   - Grounded in curated CWE/OWASP vulnerability playbooks with tailored contextual code diffs and targeted JUnit test suggestions.
2. **No external AI integration:**
   - The demo does not send repository contents, findings, or credentials to an external AI provider.
   - Explanations remain reproducible because they are generated from deterministic static-analysis rules.

---

## GitHub Webhook Configuration & Idempotency

ImpactGraph integrates with GitHub Pull Request workflows to automatically evaluate change impact and security posture.

### Webhook Setup in GitHub
1. Go to repository **Settings > Webhooks > Add webhook**.
2. **Payload URL:** `https://your-domain.com/api/github/webhook`
3. **Content type:** `application/json`
4. **Secret:** Configured value matching `GITHUB_WEBHOOK_SECRET`.
5. **Which events would you like to trigger this webhook?:** Select **Let me select individual events > Pull requests**.

### Security & Deduplication Architecture
- **HMAC-SHA256 Signature Verification:**
  - Incoming requests must supply `X-Hub-Signature-256: sha256=<signature>`.
  - Verified using constant-time comparison (`MessageDigest.isEqual`) against the configured secret.
  - If `GITHUB_WEBHOOK_SECRET` is not set or the signature is invalid, the request is rejected with `401 Unauthorized`.
- **Three-Tier Concurrency-Safe Deduplication:**
  1. *In-Memory Fast Filter:* Atomic `ConcurrentHashMap.newKeySet().add(deliveryId)` discards in-flight duplicate deliveries within the same JVM instance.
  2. *Durable Pre-Check:* `runRepository.existsByDeliveryId(deliveryId)` detects deliveries recorded in prior executions or JVM lifecycles.
  3. *Database Unique Constraint (`uk_runs_delivery_id`):* Distributed or concurrent worker races are caught upon `runRepository.saveAndFlush(run)` by intercepting `DataIntegrityViolationException`, gracefully returning `{"status": "IGNORED", "reason": "Duplicate delivery ID"}`.

---

## Verification & Validation

Both test suites and builds have been verified:
- **Backend Tests:** **223 automated tests** pass with `0 failures, 0 errors, 0 skipped` (including unit, integration, AST analyzer, rule engine, multi-threaded webhook concurrency, finding verification, RBAC authorization, and H2 database constraint tests).
- **Frontend Tests:** **37 tests across 20 suites** pass with `0 failures` (covering impact calculation, authentication, session lifecycle, security navigation, repository import, revision comparison, public transparency, and offline fallback).
- **Frontend Linter (`oxlint`):** no warnings or errors.
- **Frontend Production Build (`vite build`):** Compiled cleanly into production bundles in `dist/`.

---

## Known Limitations & Operational Considerations

1. **Local vs. Live PostgreSQL Verification:**
   - Automated CI/CD test suites run against in-memory H2 database.
   - Live PostgreSQL schema compatibility has not been executed against a production cluster during testing to prevent unintended database mutations.
2. **AST Source Code Scope:**
   - AST analysis parses Java source files located in the local project workspace.
   - For pull request webhooks in remote environments, the webhook handler must be paired with a CI runner or repository checkout worker that clones the target commit.
3. **Distributed In-Memory Cache:**
   - The first-tier deduplication set is process-local. In a horizontally scaled cluster, deduplication across nodes relies durably on database uniqueness constraints (`uk_runs_delivery_id`). A distributed cache (e.g., Redis) can be introduced for high-throughput webhook ingress.
4. **Automated Schema Evolution:**
   - Spring Boot runs with `spring.jpa.hibernate.ddl-auto=update` in development. Production environments should adopt versioned migration tools (Flyway or Liquibase).

