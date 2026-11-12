# PW Aquatics

Premium online shop for ornamental freshwater shrimp and other aquatic invertebrates.
(Repository name: `wakupwirbellose`.)

**Status:** see [Roadmap progress](docs/ROADMAP.md#progress).

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

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — system design, modules, frontend structure, security, risks, open questions
- [Database](docs/DATABASE.md) — schema, conventions, migrations
- [API](docs/API.md) — REST conventions and endpoints
- [Roadmap](docs/ROADMAP.md) — development phases
- [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) — rules for coding agents and developers

## Stack

React · TypeScript · Vite · Tailwind CSS · React Router — Java · Spring Boot · Spring Security · Spring Data JPA — PostgreSQL — Docker Compose
