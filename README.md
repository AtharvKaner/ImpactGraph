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

Database credentials and secrets are **strictly externalized** and not hardcoded into configuration files.

| Variable Name | Default Value | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `impactgraph` | PostgreSQL database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | *(empty)* | Database password |
| `SPRING_DATASOURCE_URL` | *(derived from DB_HOST/PORT/NAME)* | Full JDBC URL override |
| `PORT` | `8080` | Backend HTTP server port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Allowed frontend origins |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Frontend backend API target |

A template is provided in both `.env.example` and `backend/.env.example`.

---

## Exact Windows Execution Commands

Open two PowerShell terminals: one for the backend and one for the frontend.

### Terminal 1: Backend (Spring Boot)

1. **Navigate to the backend directory:**
   ```powershell
   cd backend
   ```

2. **Run backend automated tests (JUnit + Mockito):**
   ```powershell
   .\mvnw.cmd clean test
   ```

3. **(Optional) Package the backend executable JAR:**
   ```powershell
   .\mvnw.cmd package -DskipTests
   ```

4. **Set environment variables (if running against local PostgreSQL):**
   ```powershell
   $env:DB_HOST = "localhost"
   $env:DB_PORT = "5432"
   $env:DB_NAME = "impactgraph"
   $env:DB_USERNAME = "postgres"
   $env:DB_PASSWORD = "your_postgres_password"
   ```

   *(In Command Prompt `cmd.exe`, use `set DB_PASSWORD=your_postgres_password` instead.)*

5. **Start the backend application:**
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   *Backend starts at `http://localhost:8080`.*

6. **Verify the health check directly in your browser or via curl:**
   ```powershell
   curl.exe http://localhost:8080/api/health
   ```
   *Expected Response:*
   ```json
   {
     "status": "UP",
     "service": "ImpactGraph Backend",
     "timestamp": "2026-10-22T00:50:00.123456Z"
   }
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

## Verification & Validation

Both test suites and builds have been verified:
- **Backend Tests:** 71 automated tests pass with `0 errors, 0 failures` (covering CRUD, validation rules, cross-project checks, self-dependency rejection, duplicate detection, graph engine compatibility, and health check preservation).
- **Frontend Build:** `vite build` and `oxlint` compile with `0 warnings, 0 errors`.

---

## Roadmap

- **Milestone 1 (Complete):** Initial runnable skeleton, environment-based configuration, health check endpoint, React UI.
- **Milestone 2 (In Progress):** Core Backend & Data Model (Projects, Services, APIs, Dependencies, Integrity Constraints), CNS Security.
- **Milestone 3:** Core Software Change Impact Analysis engine, AST/dependency parsing, and React Flow graph visualization.
