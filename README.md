# Budget Tracker

[![CI](https://github.com/saakethsrikakolapu/budget-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/saakethsrikakolapu/budget-tracker/actions/workflows/ci.yml)

A privacy-first budget tracker for students. No bank login, just upload your statements.

Upload bank or credit card CSV files; the app categorizes transactions, tracks monthly budgets, and detects recurring charges. It never connects to your bank account.

**Live demo:** https://budget-tracker-saaketh1.vercel.app (free hosting; the first visit may take about a minute while the server wakes up). Try it with the [sample statement](samples/capital-one-credit-card.csv).

## Tech stack

- **Backend:** Java 25, Spring Boot 4, Spring Data JPA, PostgreSQL, Flyway
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, Recharts
- **Dev and CI:** Docker Compose, GitHub Actions

## Run locally

Requires Java 25, Node 22+, and Docker.

```bash
cp .env.example .env            # then set DB_PASSWORD in .env
docker compose up -d            # start Postgres
cd backend && ./mvnw spring-boot:run          # terminal 1: API on :8080
cd frontend && npm install && npm run dev     # terminal 2: app on :5173
```

Open http://localhost:5173. The API alone: http://localhost:8080/api/health returns `{"status":"UP","database":"UP"}`.

To try an import, create an account and upload [`samples/capital-one-credit-card.csv`](samples/capital-one-credit-card.csv) (fake data).

Tests (`./mvnw test`) start their own throwaway Postgres with Testcontainers, so they only need Docker running.

## Deployment

| Part | Host | Config |
|---|---|---|
| Frontend | Vercel (root directory `frontend`) | [`frontend/vercel.json`](frontend/vercel.json) forwards `/api/*` to the backend and serves `index.html` for app routes |
| Backend | Render web service (Docker, root directory `backend`) | [`backend/Dockerfile`](backend/Dockerfile), `prod` profile in [`application-prod.properties`](backend/src/main/resources/application-prod.properties) |
| Database | Neon Postgres | Schema created by Flyway on startup |

Backend environment variables: `DATABASE_URL` (JDBC URL with `sslmode=require`), `DATABASE_USERNAME`, `DATABASE_PASSWORD`. The free backend sleeps when idle, so the first visit can take about a minute while it wakes.

## Status

Working today:
- Secure accounts (Spring Security sessions stored in Postgres, CSRF protection)
- Capital One credit card CSV import with validation, duplicate detection, and undo
- Categories: defaults for every user, mapped automatically from the bank's labels, editable by hand
- Auto-categorization rules ("description contains POSHMARK -> Shopping"), applied retroactively
- Monthly budgets per category with progress and over-budget warnings
- Dashboard: spending by category, 6-month trend, and budget status

Next: a column-mapping screen so CSVs from any bank work.
