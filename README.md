# Wakup Wirbellose

Premium online shop for ornamental freshwater shrimp and other aquatic invertebrates.

**Status:** frontend foundation and content pages built (Roadmap Phases 0–1, frontend side). Backend not started.

## Run the frontend

```bash
cd frontend
npm ci
npm run dev
```

Then open http://localhost:5173. See [AGENTS.md](AGENTS.md#5-commands) for lint, test and build commands.

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — system design, modules, frontend structure, security, risks, open questions
- [Database](docs/DATABASE.md) — schema, conventions, migrations
- [API](docs/API.md) — REST conventions and endpoints
- [Roadmap](docs/ROADMAP.md) — development phases
- [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) — rules for coding agents and developers

## Stack

React · TypeScript · Vite · Tailwind CSS · React Router — Java · Spring Boot · Spring Security · Spring Data JPA — PostgreSQL — Docker Compose
