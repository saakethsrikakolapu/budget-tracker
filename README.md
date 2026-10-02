# Budget Tracker

A privacy-first budget tracker for students. No bank login, just upload your statements.

Upload bank or credit card CSV files; the app categorizes transactions, tracks monthly budgets, and detects recurring charges. It never connects to your bank account.

## Tech stack

- **Backend:** Java 25, Spring Boot 4, Spring Data JPA, PostgreSQL, Flyway
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, Recharts
- **Dev and CI:** Docker Compose, GitHub Actions

## Run locally

Requires Java 25 and Docker.

```bash
cp .env.example .env            # then set DB_PASSWORD in .env
docker compose up -d            # start Postgres
cd backend && ./mvnw spring-boot:run
```

Check it: http://localhost:8080/api/health returns `{"status":"UP","database":"UP"}`.

Tests (`./mvnw test`) start their own throwaway Postgres with Testcontainers, so they only need Docker running.

## Status

Early development. Stage 0: connecting frontend, backend, and database.
