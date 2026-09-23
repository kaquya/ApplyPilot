# ApplyPilot

A workspace for the entire job application process, starting with Switzerland.

Save a vacancy, choose the right CV, track each conversation and prepare the next step. The application record keeps the job description, salary, contact, documents and interview preparation together.

## Run locally

Requirements: Node.js 24 and Java 21+. Maven is downloaded by the included wrapper. External service accounts are optional for the tracker.

In one terminal:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

In another:

```powershell
cd frontend
npm ci
npm run dev
```

Open **http://localhost:3000**. The preview shows explicitly fictional examples. Create an account to start an empty, persistent workspace. Preview changes are temporary and are never copied into an account.

On macOS/Linux use `./mvnw` instead of `mvnw.cmd`.

Local development uses a file-backed H2 database in `backend/data` and private files in `backend/uploads`. PostgreSQL is used by Docker and CI. Frontend requests are proxied to the backend, keeping authentication on the same origin. Only the UI needs to be exposed by a reverse proxy.

## Run with Docker

Copy `.env.example` to `.env`, set a strong `POSTGRES_PASSWORD`, then run:

```sh
docker compose up --build -d
```

The application listens on http://localhost:3000. PostgreSQL and the API are private to the Docker network. Named volumes persist database records, sessions and uploaded files. **Do not remove these volumes unless you intend to erase the data.**

See [deployment and integrations](docs/deployment.md) for Coolify, HTTPS, Google sign-in, object storage, OpenAI, Stripe and Resend configuration.

## Included

- Application list and board, search, status filters and a dashboard derived from saved records.
- Interested → Applied → Interview → Offer → Rejected, with editable status and dates.
- Swiss cantons, CHF annual gross salary, workload percentage and Europe/Zurich interview times.
- Vacancy URL and description, contacts, notes and follow-up dates. Links are saved without scraping the source site.
- Multiple CV profiles, PDF/text upload, text extraction and private document download.
- Requirement analysis with quoted CV evidence. Unverifiable quotes cannot produce a matched result.
- Tailored cover-letter drafts, conditional CV suggestions and vacancy-specific interview preparation.
- In-app follow-up reminders after eight days, explicit reminder dates and calendar exports.
- Optional verified, opt-in daily email reminders through Resend.
- English, German, French and Polish interface; independently selectable document generation language.
- Password accounts, optional Google OAuth, server-side sessions, CSRF protection and account-scoped records/files.
- Optional Stripe Checkout, subscription portal, signed webhooks and server-enforced plan limits.
- Data export, Flyway migrations, Docker services and CI against PostgreSQL.

## Product defaults

Switzerland is the initial market: CHF is the only salary currency; salary is annual gross compensation at the recorded workload. Interview times use Swiss civil time, including daylight-saving changes. A date-only follow-up has no invented time. Language and CV profile are separate choices.

AI requests are explicit and send only the vacancy, role/company, selected CV text and output language. They use the Responses API with `store: false`; this setting does not promise zero provider retention. Documents and descriptions are untrusted input. All generated content remains a draft for user review. Changing the selected CV, vacancy, company, title or generation language invalidates saved AI results.

Billing is disabled by default. Enabling it enforces Free (10 applications), Plus (analysis, 100 AI attempts/month), Pro (all assistance, 200 attempts/month) and Lifetime (all assistance, 50 attempts/month). Attempts are reserved before provider calls, including calls that fail, to bound costs. Price amounts are configured in Stripe; the UI displays the proposed CHF 7.90 / 12.90 / 99 prices. Existing subscribers change plans in Stripe's portal. Lifetime purchasers cannot start another plan from the app.

## Verification

```sh
cd frontend
npm ci
npm run build
```

```sh
cd backend
./mvnw verify
```

The backend suite covers account isolation, real session login, CSRF, stale updates, unsafe URLs, salary validation, upload validation, private downloads, missing integrations, evidence grounding and webhook signatures. CI runs the same integration tests against PostgreSQL with Java 21. Without `TEST_DATABASE_URL`, tests use an isolated H2 database.

An optional HTTP smoke test exercises the running local stack, including the frontend proxy:

```sh
node scripts/smoke.mjs http://localhost:3000
```

It creates two disposable accounts with `@example.invalid` addresses, cleans up its documents and application records, and leaves those empty test accounts in the local database. Do not run it against a shared deployment.

## Current boundaries

This is an initial implementation for review and testing, not a declaration of production launch readiness. Provider-backed operations need real configuration and sandbox verification. See [release readiness](docs/release-readiness.md).

PDF and UTF-8 text are supported; scanned PDFs need their text pasted manually. DOCX and OCR are not implemented. Job URLs are saved manually; automatic importing, browser extensions and job-board scraping are not implemented. The dashboard measures current statuses, not a complete history of every status change. No employer messages or job applications are sent automatically.

Password reset, self-service account deletion, advanced analytics, a complete audit trail and support operations remain follow-up work. A launch also needs operator details, appropriate privacy/terms text, retention rules, backups and a tested incident/recovery process.
