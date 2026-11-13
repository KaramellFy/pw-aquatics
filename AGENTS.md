# AGENTS.md — Development instructions for coding agents

This file is the **single source of truth** for how AI coding agents (Codex,
Claude Code and others) and human developers work in this repository.
`CLAUDE.md` imports this file; put shared rules here, not there.

---

## 1. Project

**PW Aquatics** — a premium, custom-built online shop for ornamental
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

- **Frontend:** Phase 0 (foundation) and Phase 1 (design system and content
  pages) are built: Home, About, Gallery, Contact, legal page shells, and
  placeholder pages for shop, cart and account.
- **Backend:** Phase 0 foundation (Spring Boot 4.1, Java 25, Flyway, security with
  CSRF cookie, Problem Details errors, outbox email worker, rate limiting) and the
  Phase 1 contact endpoint are built. Everything not explicitly opened in
  `SecurityConfig` is denied until authentication arrives (Phase 3).
- **Phase 2 (catalog)** is built end to end: public catalog API with filters and
  facets, shop/product/species pages.
- **Phase 3 (accounts)** is built end to end: registration with email verification,
  session login, password reset, profile, address book, account deletion. Customer
  endpoints live under `/api/v1/account/**` and take the user from the session
  (`@AuthenticationPrincipal ShopUserPrincipal`) — never from request data.
- **Phase 4 (cart)** is built end to end: guest cart (`CART_TOKEN` cookie) and
  customer cart under `/api/v1/cart`, merged after sign-in. The cart stores only
  variant ids and quantities; prices, totals and VAT are recalculated on every
  call (`CatalogLookup` + `pricing`).
- **Phase 5 (checkout & orders)** is built end to end: shipping methods with dispatch
  weekdays and shipping pause, quote, order placement (`/api/v1/checkout/**`), bank
  transfer, order emails, order history, customer cancel and expiry of unpaid orders
  (`/api/v1/account/orders/**`). The shop delivers to the EU and the United Kingdom
  with DHL, dispatching Monday to Wednesday. Marking orders as paid and shipping them
  arrives with the admin tools (Phase 7).
- **Phase 6 (admin I)** is built end to end: `/admin` (administrators only) for products,
  pack sizes, photos, categories, species, colours, grades and stock. Admin endpoints live
  under `/api/v1/admin/**` in their feature modules.
- **Card payments with Stripe** (from Phase 9, built early) are built end to end: card (with
  Apple Pay / Google Pay as wallets) next to bank transfer, payment page with Stripe's Payment
  Element (`/checkout/pay/:orderNumber`), PaymentIntents from the stored total
  (`POST /api/v1/payments/stripe/intents`), signed and idempotent webhook
  (`POST /api/v1/webhooks/stripe`), 60-minute expiry of unpaid card orders. PayPal follows.
- **Dashboard & statistics** (from Phase 8, built early) are built end to end: the admin start
  page with key figures, charts and CSV export, served by the read model in the `admin`
  module (`/api/v1/admin/dashboard`, `/api/v1/admin/stats/**`). Coupon statistics follow
  with coupons.

- **Design refresh** (follow-up to Phase 1) is built: Home and About rewritten in EN/DE, and a
  motion system with Motion (`motion`) behind `LazyMotion` — tokens in `lib/motion`, shared
  pieces in `components/motion`, animated `Dialog`/`Sheet` (see §8 Motion and ADR-011).

Work only on the phase the owner asked for; do not pull later-phase features forward.

## 3. Tech stack

- **Backend:** Java 25, Spring Boot 4.x (Web MVC, Security, Data JPA, Validation,
  Mail, Actuator), Spring Session JDBC, Flyway, PostgreSQL 18, MapStruct,
  springdoc-openapi, Gradle (Kotlin DSL).
- **Frontend:** React 19, TypeScript 6 (strict), Vite 8, Tailwind CSS 4,
  React Router 8 (data mode), TanStack Query, React Hook Form + Zod, Radix UI,
  Motion (`motion`), react-i18next, Vitest. TypeScript stays on 6.x until typescript-eslint supports 7.
- **Infra:** Docker, Docker Compose, Caddy, GitHub Actions.

Do not add a new major dependency or framework without stating why in the PR
and updating the docs.

## 4. Repository layout (target)

```
backend/    Spring Boot API        → backend/src/main/java/com/pwaquatics/shop/<module>/{api,application,domain,infrastructure}
frontend/   React SPA              → frontend/src/{app,lib,components,features,hooks,styles}
infra/      Caddy, Postgres scripts
docs/       Architecture docs and ADRs
```

## 5. Commands

> Keep this section accurate whenever scripts change.

**Frontend** (`frontend/`, Node ≥ 22.22):

```bash
npm ci                 # install exact dependencies
npm run dev            # http://localhost:5173, proxies /api and /media to :8080
npm run lint           # ESLint
npm run format:check   # Prettier (npm run format to fix)
npm run typecheck      # TypeScript
npm test               # Vitest
npm run build          # production build into dist/
npm run generate:api   # regenerate src/lib/api/schema.d.ts from the running backend (dev profile); commit the result
```

**Backend** (`backend/`, Java 25, Docker for tests):

```bash
./gradlew test                        # unit + integration tests (Testcontainers PostgreSQL)
./gradlew bootRun --args='--spring.profiles.active=dev'   # API on :8080 (needs postgres + mailpit running)
./gradlew bootJar                     # build/libs/app.jar
```

Behind a Docker Hub rate limit, run tests with `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX=mirror.gcr.io/`.

**Full stack** (repository root):

```bash
cp .env.example .env                  # set POSTGRES_PASSWORD
docker compose up -d --build          # postgres (host port 5433), mailpit (http://localhost:8025), backend (:8080); --build picks up code changes
docker compose up -d postgres mailpit # infrastructure only, when running the API with bootRun
```

API docs (dev profile only): http://localhost:8080/swagger-ui.html

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
- **Personal data:** every new place that stores personal data gets a row in
  docs/DATABASE.md §6 (purpose, legal basis, retention) and, unless a law requires
  keeping it, a deletion path (`app.retention.*` job or account deletion via
  `UserAnonymised`). Consent is recorded with time and source in the audit log.
  Payment card or bank data of customers is never stored.
- **Auth rules:** emails are normalised with `EmailAddress.normalise` before storing or
  querying. Responses must not reveal whether an account exists (see docs/API.md §2.1).
  After a password change, reset or account deletion, end the user's sessions via
  `SessionManager`. Record security-relevant actions with `AuditService`.
- **Cart rules:** a module reads catalog data only through `CatalogLookup`
  (never the catalog repositories). Money arithmetic happens only in
  `pricing.domain.PriceCalculator`. Random tokens and their hashes come from
  `common.crypto.Tokens`; only the hash is stored.
- **Order rules:** an order is placed in one transaction: lock the cart
  (`CartCheckout`), check the idempotency key, lock the products (`CatalogStock`),
  only then read prices and stock, and take stock through `InventoryService` (which
  writes the ledger). Orders store snapshots; never update an order's items, prices or
  addresses. Status changes go through `Order.transitionTo` / `markPaid` / `cancel`.
  Cancelling an unshipped order returns its stock (`OrderCancellation`). Calendar rules
  (dispatch days, payment deadline) use `ShippingService.SHOP_ZONE`.
- **Admin rules:** admin services carry `@PreAuthorize("hasRole('ADMIN')")` in addition to
  the URL rule, take an `Actor` (user id + IP) and record every write with `AuditService`.
  Stock is never written through the `Product` entity (`stock_units` is read-only there):
  use `CatalogStock` / `InventoryService` / `AdminInventoryService`, which write the
  ledger. Uploaded files go through `media.ImageService` only (never stored as uploaded).
- **Payment rules** (ADR-010): an order becomes paid only through a signature-verified
  webhook (`StripeWebhooks`), never because the browser says so. The amount always comes from
  the stored order. Calls to Stripe go through `StripeGateway` (tests use `FakeStripeGateway`).
  The payment module never imports the order module: it calls `PayableOrders`, which
  `OrderPayments` implements. Take locks in the order order → payment. Webhook payloads are
  reduced to ids, amounts and statuses before they are stored; never log or store card or
  billing details. Cancelling an unpaid card order cancels its PaymentIntent first
  (`OrderCancellation`).
- **Statistics rules:** dashboard figures come from the read model in `admin`
  (`StatisticsQueries`, read-only SQL; which orders count as sales is defined there once).
  Periods are whole days in `ShippingService.SHOP_ZONE` (`StatsPeriod`). Shares, changes,
  averages and bucket totals are calculated on the server; the CSV export contains no
  personal data.

## 8. Frontend conventions

- **Feature folders** under `src/features/<feature>/{api,components,data,pages}`;
  shared UI only in `src/components/ui`, site chrome in `src/components/layout`.
  Features import each other only through their `index.ts`. The router
  (`src/app/routes.tsx`) imports page modules directly and lazily, so a
  feature's `index.ts` must not re-export pages (that would defeat code splitting).
- **Server state in TanStack Query**; no global store for API data. Mutations
  invalidate or update the relevant query keys.
- **Login state** comes from `useSession()` (`GET /auth/me`); nothing auth-related is kept
  in browser storage. Wrap signed-in pages in `RequireAuth`. When an action signs the
  user out, navigate away first and clear the session afterwards (see `useLogout`),
  otherwise the guard redirects to the login page.
- **All HTTP via `src/lib/api/client.ts`** (credentials, CSRF header, Problem
  Details → `ApiError`). Use the generated types in `src/lib/api/schema.d.ts`;
  never hand-write API types that the spec already defines. Never edit generated files.
- `Price` and formatting helpers only **format** values from the API — no arithmetic on money.
- **Cart:** every cart call returns the full recalculated cart; mutations write it
  into the `queryKeys.cart` cache. Components show `lineTotal`, `totals` and
  `maxQuantity` from the API and never compute them. Invalidate `queryKeys.cart`
  whenever the signed-in user changes.
- **Checkout:** the pages only collect choices (address, shipping method, confirmations).
  Shipping options, dispatch dates, totals, available payment methods and whether an
  order can be cancelled all come from the API. An order request carries ids, the
  method code and the total the customer saw (`expectedTotal`) — never items or prices.
  Format API dates with `lib/format/date.ts`.
- **Card payment:** Stripe.js is loaded only on the payment page (`features/checkout/lib/stripe.ts`,
  `@stripe/stripe-js/pure`), with the publishable key from the API. The page calls
  `confirmPayment` and moves on; whether the order is paid comes from the API (the
  confirmation page polls the order). Never show an order as paid from Stripe's browser result.
- **Admin:** pages live in `features/admin`, routed under `/admin` with `RequireAdmin` and
  `AdminLayout`; each page is a lazy chunk and nothing from it is imported by the shop.
  Prices typed by the owner are parsed with `parseMoneyInput` ("3,90" → 390); what blocks
  publishing comes from the server (`publishProblems`). Edits send the product's `version`.
- **Charts** (admin dashboard): use the components in `features/admin/components/charts`
  (no chart library). Values, totals, shares and changes come from the API; the frontend only
  scales them to pixels and formats them (`lib/format/number.ts`). Every chart has a table view
  or prints its values, a legend for two or more series, and colours from the brand tokens
  (`brand-800`/`brand-500` for paid/awaiting payment, `brand-600` for one series) — never
  status colours as series colours.
- **Motion** (see docs/adr/011-motion-library.md): animate to explain a change, never as
  decoration. Use the curves and durations from `lib/motion/tokens.ts` (CSS: `--ease-*`,
  `--duration-*`): entrances `ease.out`, moves `ease.inOut`, exits `exitDuration(...)`
  (20 % shorter), list rows `STAGGER`; no bounce. Use `m.*` (never `motion.*`: `LazyMotion`
  is strict) and the shared pieces in `components/motion` (`SlidingIndicator` for anything
  selected, `SwapText`/`CrossfadeIcon` for changing text and icons, `MaskedWords` for
  headlines) and `components/ui/{Dialog,Sheet}` for overlays. Pressable things get `press`
  (or `Button`). Animations started by hand use rAF or `element.animate` and check
  `prefersReducedMotion()`; don't import Motion's `animate`/`useAnimate` (they put the whole
  engine in the first download). Content must be visible without JavaScript or scroll
  timelines: no fade-up-on-scroll, no hiding until revealed. Disabled buttons drop keyboard
  focus, so a button that is busy uses `aria-busy`/`aria-disabled` and ignores clicks instead.
- Forms: React Hook Form + Zod for UX validation; always display server
  validation errors too.
- Styling: Tailwind utilities and design tokens only; build variants in
  `components/ui` instead of one-off styles. No inline hex colours.
- Accessibility: semantic HTML, labels for every input, visible focus, keyboard
  support, alt text, `prefers-reduced-motion` respected. Target WCAG 2.2 AA.
- Performance: lazy-load routes (admin is its own chunk), use `ResponsiveImage`,
  avoid layout shift.
- TypeScript `strict`; no `any` without a comment explaining why.
- All user-facing text through i18n keys (English default, German second).
  `src/lib/i18n/locales/en.ts` defines the keys; `de.ts` must match it exactly
  (a test enforces this). Untranslated constants (shop name, email) live in `src/config/shop.ts`.
- **Brand & colour:** use design tokens from `src/styles/index.css`. The logo
  colour `brand-400` (#88C0DB) is never used for text (2:1 on white); use
  `brand-700` or darker for brand-coloured text. Use the official logo via
  `components/layout/Brand.tsx` / `Logo.tsx`; never redraw or recolour its shapes.
- Until real photos exist, use `<Artwork>` placeholders; switch to
  `<ResponsiveImage>` with backend renditions once images are available.

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
