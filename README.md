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
keys, the shop offers bank transfer only.

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — system design, modules, frontend structure, security, risks, open questions
- [Database](docs/DATABASE.md) — schema, conventions, migrations
- [API](docs/API.md) — REST conventions and endpoints
- [Roadmap](docs/ROADMAP.md) — development phases
- [Website gallery](docs/gallery/README.md) — screenshots, animation clips and a video tour
- [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) — rules for coding agents and developers

## Stack

React · TypeScript · Vite · Tailwind CSS · React Router — Java · Spring Boot · Spring Security · Spring Data JPA — PostgreSQL — Docker Compose
