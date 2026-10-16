# How it works

The technical side of [Spendr](../README.md): architecture, the algorithms, how accurate they
are, security, and how it's tested and deployed.

[![CI](https://github.com/saakethsrikakolapu/budget-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/saakethsrikakolapu/budget-tracker/actions/workflows/ci.yml)

## Architecture

```
React + TypeScript (Vercel, www.8022029.xyz) ──/api──▶ Spring Boot (Render) ──▶ PostgreSQL (Neon)
                                                      sessions stored in Postgres
```

- **Backend:** Java 25, Spring Boot 4 (Web, Security, Data JPA, Validation, Session JDBC), PostgreSQL, Flyway
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS 4, Recharts, Lucide icons, Inter (self-hosted)
- **ML:** Python 3.13, scikit-learn, numpy (training and evaluation); the model runs inside Java
- **Money** is `BigDecimal` in Java and `NUMERIC(12,2)` in Postgres, never floating point. The schema is
  managed only by Flyway migrations.

## Reading any bank's CSV

[`ColumnInference.java`](../backend/src/main/java/com/saaketh/budget/imports/csv/ColumnInference.java)
scores every column by its values, not its header: the earlier of two date columns is the transaction
date; a column that changes by exactly each row's amount is a running balance and is ignored; the
description is the text column with the most variety. Tested against 12 layouts (Chase, Bank of America,
Wells Fargo, Capital One, Amex, Discover, Citi, Apple Card, US Bank, PNC; fake data in
[`samples/layouts/`](../samples/layouts)). Column choices are remembered per file shape.

**Duplicates:** each row gets a SHA-256 fingerprint of its date, amount, and description plus an
occurrence number, so two identical coffees on one day both count, but re-importing a file adds
nothing. A unique (account, fingerprint, occurrence) constraint keeps it safe even if two uploads race.

## Categorization

Order: your manual choices > your "always" choices (rules) > the bank's own label > a dictionary of
480+ merchant keywords > a machine-learning suggestion > Uncategorized. Changing a choice re-sorts past
transactions, except ones set by hand.

### Machine-learning suggestions (trained in Python, served in Java)

[`ml/`](../ml) trains a TF-IDF model over character 3-5-grams with multinomial logistic regression.
Character pieces survive what breaks whole-word rules: glued words (`DOMINOSPIZZA`), text cut at about
22 characters (`HARRIS TEE`), dropped vowels (`WALGRNS`). Instead of a second service (which would also
sleep on free hosting), the model is exported as JSON and
[`CategoryModel.java`](../backend/src/main/java/com/saaketh/budget/category/CategoryModel.java) runs the
same math inside the backend; a parity test checks Java against Python's probabilities (within 1e-9) on
155 cases. It suggests only when at least 80% sure, only for spending, and never over a person's choice.

Measured on synthetic descriptions in real banks' formats (11,439 training examples, 11 spending
categories), comparing how many test descriptions end up in the right category:

| | Known merchants, new messy descriptions | Merchants never seen in training |
| --- | --- | --- |
| Rules only (dictionary) | 39.4% | 44.6% |
| Model only | 95.7% | 63.8% |
| **Rules, then the model** | **80.5%** (5.0% wrong) | **52.8%** (6.3% wrong) |
| Model suggestions right | 99.8% of 804 | 82.1% of 262 |

A bare brand name ("BOJANGLES") carries no hint of its category, so unseen merchants stay hard. The data
is synthetic and its labels come from the same generator the model learns from, so these numbers show
the model handles the patterns it was built for; real users' corrections are the next training data.
Retrain with `cd ml && python -m budget_ml.train` (details in [`ml/README.md`](../ml/README.md)).

## Suggested budget

Nobody has to type in a budget. [`BudgetPlanner.java`](../backend/src/main/java/com/saaketh/budget/budget/BudgetPlanner.java)
builds one from the person's own history:

- **Usual** spending per category is the median of the last 6 full months (statements start mid-month,
  so partial months are skipped). A month with nothing in a category counts as $0, and one big purchase
  doesn't move the median.
- **The total** is what usually comes in (paychecks, plus Zelle/Venmo/Cash App from family) when a
  checking account shows it, otherwise what usually goes out (a credit card alone shows no income).
  The person can replace it with their own number.
- **The split:** detected bills and subscriptions keep their full cost; the rest is shared in the
  same proportions as usual spending, trimmed equally if usual spending is more than the total, never
  scaled up (the difference is savings).
- **Higher than usual** compares this month so far with what had usually been spent by the same day
  of the month (so rent on the 1st isn't a spike), and needs both +25% and +$20 to flag anything.

## Subscription and bill detection

[`RecurringDetector.java`](../backend/src/main/java/com/saaketh/budget/recurring/RecurringDetector.java)
reduces descriptions to a merchant key (`PURCHASE AUTHORIZED ON 10/27 NETFLIX.COM CARD 1234` →
`NETFLIX COM`), matches each merchant's gaps to a cadence (weekly to yearly, allowing one skipped
period), and requires amounts within 25% (50% for bills with spotless timing). Computed on request, so
it's never out of date.

Measured on 200 synthetic students (18 months each, seeded), including price increases, posting-date
jitter, skipped months, varying bills, cancelled subscriptions, and look-alikes (coffee, groceries,
rides): **precision 0.999, recall 0.999** (1 false positive and 1 miss in 1,421), schedule and
active/cancelled status always right. Known limits: two one-off purchases at the same store about a year
apart can look yearly; merchants whose reference codes are all letters may not group together.

## Security, privacy, and accessibility

- Server-side sessions in an HttpOnly, SameSite=Lax, Secure cookie; CSRF tokens on every change; BCrypt
  passwords; session id rotated at login.
- Failed logins rate-limited per email (5) and per IP (30) per 15 minutes (429 with Retry-After), without
  revealing which emails exist.
- Password-reset and verification links carry 256 random bits, are stored only as SHA-256 hashes,
  expire (30 minutes / 24 hours) and work once; a reset logs out every device.
- Uploaded files are never stored; every query is scoped to the logged-in user. Deleting an account
  removes all its data (every table cascades from the user) and logs out every device.
- Changing the password asks for the current one and logs out every other device. "Download my data"
  exports every transaction as CSV, with spreadsheet formulas neutralized (CSV injection).
- The optional monthly upload reminder (off by default) goes only to confirmed addresses, at most once a
  month, and only if last month's statement isn't uploaded yet
  ([`UploadReminderService.java`](../backend/src/main/java/com/saaketh/budget/reminder/UploadReminderService.java)).
- Every page passes an [axe-core](https://github.com/dequelabs/axe-core) audit (WCAG 2.2 AA + best
  practices) in light and dark mode.

## Testing

- 247 backend tests (JUnit 5, Spring Boot tests against a real Postgres via Testcontainers):
  `cd backend && ./mvnw test`
- Frontend unit and component tests (Vitest + React Testing Library, API faked; they find elements
  by role and label like a screen reader would): `cd frontend && npm test`
- End-to-end tests (Playwright): a real Chromium signs up, uploads a statement, checks the dashboard,
  uses the suggested budget and deletes the account, at desktop and phone sizes, against the real
  backend and Postgres: `cd frontend && npm run test:e2e` (with the backend running)
- 14 Python tests for the model: `cd ml && python -m pytest`
- GitHub Actions runs all of it on every push; a failed browser test keeps its screenshot and trace.
- `node scripts/record-demo.mjs` regenerates the README demo animation from a running app, using a
  throwaway demo account.

## Deployment

| Part | Host | Config |
|---|---|---|
| Frontend | Vercel (root directory `frontend`), domain `www.8022029.xyz` | [`frontend/vercel.json`](../frontend/vercel.json) forwards `/api/*` to the backend and serves `index.html` for app routes |
| Backend | Render web service (Docker, root directory `backend`) | [`backend/Dockerfile`](../backend/Dockerfile), `prod` profile in [`application-prod.properties`](../backend/src/main/resources/application-prod.properties) |
| Database | Neon Postgres | Schema created by Flyway on startup |

Backend environment variables: `DATABASE_URL` (JDBC URL with `sslmode=require`), `DATABASE_USERNAME`,
`DATABASE_PASSWORD`; for email, `BREVO_API_KEY` and `MAIL_FROM` (Brevo's HTTPS API, because Render's free
plan blocks outgoing SMTP; `SPRING_MAIL_*` works on hosts that allow SMTP). `APP_BASE_URL` (used in email
links) defaults to `https://www.8022029.xyz`. Locally, emails are printed to the backend log.
