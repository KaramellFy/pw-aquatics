# PW Aquatics

Premium online shop for ornamental freshwater shrimp and other aquatic invertebrates.

**Status:** see [Roadmap progress](docs/ROADMAP.md#progress).

**See it without running it:** the [website gallery](docs/gallery/README.md) has screenshots
(desktop, phone, admin), animation clips and a video tour.

## Run locally

Requirements: Docker, Node ≥ 22.22, Java 25 (only to run the API outside Docker).

```bash
cp .env.example .env          # set POSTGRES_PASSWORD
docker compose up -d --build  # PostgreSQL, Mailpit and the API on :8080
```

After pulling new code, run `docker compose up -d --build` again so the API is rebuilt;
without `--build` Docker keeps running the old API, and new features (for example the
admin pages) fail with "could not be loaded".

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173. Emails sent by the shop appear in Mailpit at http://localhost:8025.
See [AGENTS.md](AGENTS.md#5-commands) for test, lint and build commands.

**Card payments (optional):** put Stripe **test** keys into `.env` (`STRIPE_SECRET_KEY`,
`STRIPE_PUBLISHABLE_KEY`) and forward Stripe's events to the local API with the
[Stripe CLI](https://docs.stripe.com/stripe-cli):

```bash
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
```

Put the `whsec_…` it prints into `.env` as `STRIPE_WEBHOOK_SECRET` and restart the API. Pay
with Stripe's test cards, e.g. `4242 4242 4242 4242` (success), `4000 0000 0000 0002`
(declined) or `4000 0025 0000 3155` (3-D Secure), any future date and any CVC. Without the
keys, the shop offers bank transfer only. Refunds are made from the admin order page
("Refund"); Stripe's test refunds succeed straight away.

**PayPal (optional):** create a REST app in the PayPal Developer Dashboard (sandbox) and put
`PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` and `PAYPAL_WEBHOOK_ID` into `.env`
(`PAYPAL_ENVIRONMENT=sandbox`). Pay with a sandbox *personal* account from the same dashboard.
PayPal cannot reach `localhost`, so no webhooks arrive locally; a payment still completes,
because the shop's server takes the money itself after the customer approves. To try the
webhooks, register `https://<public address>/api/v1/webhooks/paypal` (e.g. through a tunnel)
and use that webhook's id; otherwise any placeholder id will do locally.

**Sign in with Google (optional):** in the Google Cloud console create an OAuth client of type
"Web application" (APIs & Services → Credentials), add the authorised redirect URI
`<APP_BASE_URL>/api/v1/auth/google/callback` (locally `http://localhost:5173/api/v1/auth/google/callback`)
and put `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` into `.env`. While the consent screen is in
"Testing", only the test users listed there can sign in. See
[ADR-014](docs/adr/014-sign-in-with-google.md).

**Administrators** sign in in two steps: after the password, a code from an authenticator app
(the shop asks them to set it up on their first visit to `/admin`). Locally, scan the QR code
with any authenticator app on your phone. See
[ADR-015](docs/adr/015-two-step-sign-in-for-administrators.md).

**Pages for search engines:** in production the frontend's Caddy asks the backend to fill in
public pages (title, language links, text) before JavaScript runs, see
[ADR-013](docs/adr/013-pages-filled-in-by-the-backend.md). Nothing to set up for `npm run dev`:
the Vite dev server serves pages as before.

## Run it on a server

`docker-compose.prod.yml` runs the whole shop on one server with automatic HTTPS, nightly
backups and a restore command. Setup, email (SPF/DKIM/DMARC), payments, backups, monitoring,
updates and what to do when something goes wrong: [docs/OPERATIONS.md](docs/OPERATIONS.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — system design, modules, frontend structure, security, risks, open questions
- [Database](docs/DATABASE.md) — schema, conventions, migrations
- [API](docs/API.md) — REST conventions and endpoints
- [Roadmap](docs/ROADMAP.md) — development phases
- [Operations](docs/OPERATIONS.md) — running the shop on a server: setup, backups, monitoring, incidents
- [Website gallery](docs/gallery/README.md) — screenshots, animation clips and a video tour
- [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) — rules for coding agents and developers

## Stack

React · TypeScript · Vite · Tailwind CSS · React Router — Java · Spring Boot · Spring Security · Spring Data JPA — PostgreSQL — Docker Compose
