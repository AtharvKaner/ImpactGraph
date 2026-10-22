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
│   │   │   │   └── controller/HealthController.java # /api/health endpoint
│   │   │   └── resources/
│   │   │       └── application.properties      # PostgreSQL config via env vars
│   │   └── test/
│   │       ├── java/com/impactgraph/
│   │       │   ├── ImpactGraphApplicationTests.java
│   │       │   └── controller/HealthControllerTest.java
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

## Verification & Validation

Both test suites and builds have been verified:
- **Backend Tests:** `HealthControllerTest` and `ImpactGraphApplicationTests` pass with `0 errors, 0 failures`.
- **Frontend Build:** `vite build` and `oxlint` compile with `0 warnings, 0 errors`.

---

## Roadmap

- **Milestone 1 (Complete):** Initial runnable skeleton, environment-based configuration, health check endpoint, React UI.
- **Milestone 2:** CNS Security implementation (Authentication, Authorization, RBAC, and Audit Logging).
- **Milestone 3:** Core Software Change Impact Analysis engine, AST/dependency parsing, and React Flow graph visualization.
