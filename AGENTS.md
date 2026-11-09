# AGENTS.md — Development instructions for coding agents

This file is the **single source of truth** for how AI coding agents (Codex,
Claude Code and others) and human developers work in this repository.
`CLAUDE.md` imports this file; put shared rules here, not there.

---

## 1. Project

**Wakup Wirbellose** — a premium, custom-built online shop for ornamental
freshwater shrimp and other aquatic invertebrates. We sell **live animals**:
stock can shrink without a sale, shipping depends on weekdays and weather,
and some species may not be traded legally.

| Read before working | Purpose |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, modules, flows, security, risks |
| [docs/DATABASE.md](docs/DATABASE.md) | Schema, conventions, migrations |
| [docs/API.md](docs/API.md) | REST conventions and endpoints |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phases and what belongs in each |

## 2. Current status

**Planning phase.** The architecture is a proposal awaiting the owner's
approval. **Do not start implementing application code until the owner has
approved the plan and named the phase to work on.** Work only on the phase you
were asked to do; do not pull later-phase features forward.

## 3. Tech stack

- **Backend:** Java 25, Spring Boot 4.x (Web MVC, Security, Data JPA, Validation,
  Mail, Actuator), Spring Session JDBC, Flyway, PostgreSQL 18, MapStruct,
  springdoc-openapi, Gradle (Kotlin DSL).
- **Frontend:** React 19, TypeScript (strict), Vite, Tailwind CSS 4,
  React Router 7, TanStack Query, React Hook Form + Zod, Radix UI, react-i18next.
- **Infra:** Docker, Docker Compose, Caddy, GitHub Actions.

Do not add a new major dependency or framework without stating why in the PR
and updating the docs.

## 4. Repository layout (target)

```
backend/    Spring Boot API        → backend/src/main/java/de/wakup/shop/<module>/{api,application,domain,infrastructure}
frontend/   React SPA              → frontend/src/{app,lib,components,features,hooks,styles}
infra/      Caddy, Postgres scripts
docs/       Architecture docs and ADRs
```

## 5. Commands

> Filled in during Phase 0. Keep this section accurate whenever scripts change.

```bash
docker compose up -d                  # full local stack (postgres, backend, mailpit)
cd backend && ./gradlew test          # backend tests (needs Docker for Testcontainers)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
cd frontend && npm ci && npm run dev  # SPA on http://localhost:5173 (proxies /api)
cd frontend && npm run lint && npm run typecheck && npm test
cd frontend && npm run generate:api   # regenerate API types from the backend OpenAPI spec
```

---

## 6. Non-negotiable rules

1. **Clean, modular architecture.** Respect module boundaries (see §7). No
   cross-module repository access.
2. **No business logic in frontend components.** Components render data and
   collect input. Prices, totals, discounts, VAT, stock, availability and order
   state come from the API.
3. **Validate every important operation server-side**, even if the frontend
   already validated it.
4. **Never trust prices, totals or stock sent by a client.** Requests carry IDs
   and quantities only. The server loads prices from the database.
5. **Authentication and role-based access on every endpoint.** Admin endpoints
   require `ADMIN` at the URL level *and* via method security. Customer
   resources are always filtered by the current user (another user's resource → 404).
6. **Schema changes only via Flyway migrations.** Never edit a merged
   migration. Hibernate runs with `ddl-auto=validate`.
7. **Never store or log payment card details.** Payments go through Stripe /
   PayPal hosted components; we keep only provider references.
8. **Secrets only in environment variables.** Never commit `.env`, keys,
   passwords or tokens. Add new variables to `.env.example` with a comment.
9. **Write maintainable, documented code.** Clear names, small units, Javadoc/TSDoc
   on public services and non-obvious logic, docs updated with the change.
10. **Follow security best practices** (§10).

If a request conflicts with these rules, stop and ask the owner instead of
working around them.

---

## 7. Backend conventions

- **Package by feature**, layered inside each module:
  `api` (controllers, DTO records, mappers) → `application` (use cases,
  `@Transactional`) → `domain` (entities, value objects, rules) →
  `infrastructure` (repositories, external adapters).
- Controllers are thin: validate, call one service, map the result.
- Entities never appear in API responses; use DTO `record`s + MapStruct.
- Domain rules live in the domain (`Order.transitionTo`, `PriceCalculator`) and
  are unit-tested without Spring.
- **Money:** `long` cents wrapped in `Money`; never `double`/`float`.
  Prices are gross (incl. VAT).
- **Time:** `Instant`/`timestamptz` UTC; inject `Clock`; calendar rules use `Europe/Berlin`.
- **IDs:** UUIDv7 generated in the application.
- **Errors:** throw domain exceptions; the global handler maps them to RFC 9457
  Problem Details with a stable `code` (see docs/API.md §1.4).
- **Concurrency:** `@Version` on aggregates; stock decrement via conditional
  update after `SELECT … FOR UPDATE` in deterministic order.
- Side effects (emails) go through the **outbox**, never inside the HTTP request.
- Configuration via `@ConfigurationProperties` records, validated at startup.
- No `System.out`, no swallowed exceptions, no personal data in logs.

## 8. Frontend conventions

- **Feature folders** under `src/features/<feature>/{api,components,pages}`;
  shared UI only in `src/components/ui`. Features import each other only through
  their `index.ts`.
- **Server state in TanStack Query**; no global store for API data. Mutations
  invalidate or update the relevant query keys.
- **All HTTP via `src/lib/api/client.ts`** (credentials, CSRF header, Problem
  Details → `ApiError`). Use the generated types in `src/lib/api/schema.d.ts`;
  never hand-write API types that the spec already defines. Never edit generated files.
- `Price` and formatting helpers only **format** values from the API — no arithmetic on money.
- Forms: React Hook Form + Zod for UX validation; always display server
  validation errors too.
- Styling: Tailwind utilities and design tokens only; build variants in
  `components/ui` instead of one-off styles. No inline hex colours.
- Accessibility: semantic HTML, labels for every input, visible focus, keyboard
  support, alt text, `prefers-reduced-motion` respected. Target WCAG 2.2 AA.
- Performance: lazy-load routes (admin is its own chunk), use `ResponsiveImage`,
  avoid layout shift.
- TypeScript `strict`; no `any` without a comment explaining why.
- All user-facing text through i18n keys (German default).

## 9. Database conventions

See docs/DATABASE.md §1. In short: snake_case plural tables, UUID PKs, money in
`_cents` (`bigint`), percentages in `_bps`, `timestamptz`, enums as `varchar` +
`CHECK`, indexes on every foreign key, snapshots in orders.

Migration file names: `V<yyyy>_<MM>_<dd>_<HHmm>__<description>.sql` in
`backend/src/main/resources/db/migration`. Dev-only seed data goes in `db/seed-dev`.

## 10. Security checklist (every change)

- [ ] Endpoint has explicit access rules; admin endpoints tested for 401/403.
- [ ] Ownership checked for customer resources.
- [ ] All input validated (types, ranges, lengths); no client-supplied prices/stock used.
- [ ] State-changing requests protected by CSRF (webhooks: signature check instead).
- [ ] No secrets, tokens, passwords or card data in code, logs, responses or test fixtures.
- [ ] Rate limiting considered for public write endpoints.
- [ ] Uploads: size limit, content-type sniffing, re-encoding.
- [ ] Responses don't leak whether an email/account exists.
- [ ] Error responses contain no stack traces or internals.

## 11. Testing requirements

- **Backend:** unit tests for domain logic; integration tests with
  Testcontainers PostgreSQL (no H2); MockMvc tests for each endpoint covering
  success, validation errors, 401/403 and ownership.
- **Critical scenarios must stay tested:** concurrent purchase of the last
  units, idempotent order placement, manipulated client prices ignored, illegal
  order status transitions rejected, coupon limits under concurrency.
- **Frontend:** Vitest + Testing Library for components/hooks with logic;
  Playwright for main journeys.
- Do not delete, skip or weaken a failing test to make the build pass — fix the cause.

## 12. Git workflow

- Branches: `feature/<phase>-<short-name>`, `fix/<short-name>`, `docs/<short-name>`
  (or the branch name you were assigned).
- Commits: Conventional Commits — `feat(catalog): add grade filter`,
  `fix(cart): cap merged quantity at stock`, `docs: update API for coupons`.
- Small, focused PRs; describe what changed, why, how it was tested, and any
  migration or new environment variable.
- Never commit generated build output, `.env` files, or IDE settings.
- Never force-push shared branches.

## 13. Documentation duty

Update the docs in the same change when you:
- add or change a table/column → `docs/DATABASE.md`
- add or change an endpoint, field or error code → `docs/API.md`
- change architecture, a module boundary or a dependency → `docs/ARCHITECTURE.md` (+ ADR in `docs/adr/`)
- finish or re-scope roadmap items → `docs/ROADMAP.md`
- change commands or setup → this file and `README.md`

## 14. Definition of done

- Feature works end-to-end (API + UI) and matches the docs.
- Server-side validation and authorisation in place.
- Tests added and passing; lint and type-check clean.
- Migrations included and run on a fresh database.
- Responsive (mobile → desktop) and accessible.
- Docs updated. No TODOs left without a linked issue.

## 15. When unsure

Ask the owner rather than guessing on: legal texts, tax rates, shipping rules,
pricing logic, anything irreversible, or anything that changes the approved
architecture.
