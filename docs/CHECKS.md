# PW Aquatics — Security, performance and accessibility checks

What was checked before launch, what it found, what was fixed, which findings were accepted and
why, and how to repeat each check. Repeat them on the real server before the shop opens
(docs/OPERATIONS.md §10), after larger changes and every few months.

---

## 1. Latest results (18 November 2026)

Checked on a local copy of the production stack (`docker-compose.prod.yml`: edge Caddy with
HTTPS, frontend, backend, PostgreSQL) with the demo catalog. There were no real photos yet, so
product and gallery pages showed placeholder art.

### 1.1 Security

| Check | Result |
|---|---|
| OWASP ZAP baseline: spider and passive rules over all public pages | 0 failures, 4 warnings (accepted, below). Fixed: photos (`/media/**`) set the `XSRF-TOKEN` cookie on responses that browsers and caches keep for a year; photos now have their own security rules without cookies or session (`SecurityConfig.mediaSecurity`). |
| OWASP ZAP API scan: active attacks (injection, path traversal, …) on all 132 operations of the API description, signed out, with a valid CSRF token so requests reach the handlers | 0 failures. Fixed: a query parameter without a name (`?=x`) answered 500 on the product list, filters and gallery; now `400 MALFORMED_REQUEST` (`ApiExceptionHandler`). |
| Two-step sign-in for administrators | Built (ADR-015). |
| npm, production dependencies (`npm audit --omit=dev`) | 0 known vulnerabilities. |
| npm, build tools | `picomatch` ReDoS ([GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf)) through Vite, Vitest and typescript-eslint. No fixed version exists yet; it only matches our own glob patterns at build time and is not in the shipped app. Accepted; Dependabot proposes the update once there is one. |
| Java dependencies (OSV database; Trivy on the backend image) | 0 known high or critical. |
| Container images (Trivy, high and critical) | Backend 0. Edge and frontend (Caddy 2): CVE-2026-116587 (go-jose v3, no fix yet) and CVE-2026-78661 (golang.org/x/net, needs a Caddy release with the newer library); Caddy is kept current by the monthly image refresh. PostgreSQL: operating-system packages 0; `gosu`, the helper that only switches from root to the `postgres` user when the container starts, is built with an old Go (1 critical, 25 high in Go's standard library, none reachable). A local copy of the PostgreSQL image that was two weeks old showed 20 more (krb5, libxslt) that the current image has fixed — the reason for the monthly `docker compose build --pull` (docs/OPERATIONS.md §8). |

Accepted warnings:

- **The Content-Security-Policy allows inline styles** (`style-src 'unsafe-inline'`). Radix dialogs
  (scroll lock) and PayPal's buttons add `<style>` elements while the page runs; removing it
  would need a new nonce on every page, which the static files Caddy serves cannot carry.
  Scripts stay strict (no inline scripts, no `eval`). Someone who could inject markup could
  restyle a page but not run code, and React escapes all text.
- **The `XSRF-TOKEN` cookie is readable by scripts** (no `HttpOnly`): on purpose, the app reads
  it and sends it back in a header (cookie-to-header CSRF protection).
- **Comment in the HTML:** `index.html` explains its markers for the backend (ADR-013); nothing
  secret.
- **Pages are sent with `Cache-Control: no-cache`:** browsers check for a new version every
  time, which is right for HTML; API answers are `no-store`.

### 1.2 Performance

Lighthouse 12.8, mobile (simulated slow 4G and a mid-range phone). Final run; the pages near 90
were run three times (Shop 88–92, Product 88–90, German shop 89–93):

| Page | Performance (before → after) | Accessibility | Best practices | SEO |
|---|---|---|---|---|
| Home | 86 → 95 | 100 | 100 | 100 |
| Shop | 77 → 91 | 98 → 100 | 100 | 100 |
| Product | 73 → 88 | 100 | 100 | 100 |
| Species | 89 → 91 | 100 | 100 | 100 |
| Gallery | 66 → 92 | 100 | 100 | 100 |
| About | 84 → 94 | 100 | 100 | 100 |
| Contact | 73 → 91 | 92 → 100 | 100 | 100 |
| Cart | 91 → 93 | 100 | 100 | 63¹ |
| Sign in | 84 → 94 | 100 | 100 | 63¹ |
| Shop (German) | 76 → 90 | 98 → 100 | 100 | 100 |

¹ Kept out of search engines on purpose (`noindex`).

Changes that got there (the main problem before was 400–600 ms of blocked main thread while the
app started, and the gallery jumping as its photos arrived): each page loads only its own
language's texts; the first render is split so the page stays responsive; the shop
page arrives with its filter data and the gallery with its first photos (no loading jump); the
cart keeps room while it loads; the backend's page and the app ask for the same photo sizes,
the first photos are fetched first and the rest when scrolled near.

The product page's largest element is still the placeholder art, painted once the app runs
(largest paint 3.0–3.2 s, score 88–90). With real photos the page's HTML already contains the main photo,
so the browser fetches it at once; measure again once the photos are in.

### 1.3 Accessibility (WCAG 2.2 AA)

| Check | Result |
|---|---|
| axe-core 4.14 (WCAG 2.0, 2.1 and 2.2 A/AA rules and best practices) on 64 views: public pages on desktop and phone (with the menu and the filter sheet open), the sign-in code step, every admin page, the account pages, cart and checkout | 0 findings |
| Lighthouse accessibility, ten main pages | 100 everywhere |
| Keyboard: Tab through eight main pages on desktop and phone | "Skip to content" comes first; every stop shows a focus ring; nothing focused is off screen, hidden or covered by the sticky header (2.4.11) |

Fixed: the language switch's names start with the visible "EN"/"DE" (2.5.3 Label in Name); the
shop's headings go h1 → h2 → h3; the contact details list is a proper description list; the
breadcrumb trail is named "Breadcrumb" (it was "Shop", like the footer menu); the packing slip
sits inside a `main` landmark.

### 1.4 Still open (needs a person or the real server)

- A screen-reader test by a person (NVDA with Firefox on Windows, VoiceOver on an iPhone), at
  least sign-in, shop, cart and checkout.
- A ZAP scan signed in as a customer and as an administrator, and a manual penetration test,
  ideally by someone outside the project, against the real server (with test data).
- Lighthouse on the real server with real photos.
- An index review of the database with realistic amounts of data.
- An OWASP Top 10 review of the code.

---

## 2. How to repeat the checks

Replace `shop.example` with the shop's domain. Never run active scans (2.1, API scan) against
the live shop: they send hundreds of malformed requests, contact messages and sign-in attempts.
Use a copy of the stack with test data.

### 2.1 Security scan (OWASP ZAP)

Passive baseline, safe against the live shop:

```bash
docker run --rm -v "$PWD:/zap/wrk" zaproxy/zap-stable zap-baseline.py -t https://shop.example -m 3 -r zap-baseline.html
```

Expected: `FAIL-NEW: 0` and the accepted warnings of §1.1. A local copy must be reached by a
name with a dot (e.g. `shop.localhost`): ZAP does not send a server name for `localhost`, and
Caddy then cannot choose a certificate.

Active API scan, against a development backend (the API description is only served with the
`dev` profile). The two replacer rules send the same CSRF token as cookie and header, so the
requests reach the handlers instead of stopping at the CSRF check:

```bash
docker run --rm --network host -v "$PWD:/zap/wrk" zaproxy/zap-stable zap-api-scan.py -t http://localhost:8080/api/v3/api-docs -f openapi -r zap-api.html -T 25 -z "-config replacer.full_list(0).description=csrfheader -config replacer.full_list(0).enabled=true -config replacer.full_list(0).matchtype=REQ_HEADER -config replacer.full_list(0).matchstr=X-XSRF-TOKEN -config replacer.full_list(0).regex=false -config replacer.full_list(0).replacement=zap-scan-token -config replacer.full_list(1).description=csrfcookie -config replacer.full_list(1).enabled=true -config replacer.full_list(1).matchtype=REQ_HEADER -config replacer.full_list(1).matchstr=Cookie -config replacer.full_list(1).regex=false -config replacer.full_list(1).replacement=XSRF-TOKEN=zap-scan-token"
```

Expected: `FAIL-NEW: 0` and no "Server Error response" warning (every 500 is a bug).

### 2.2 Dependencies and images

```bash
cd frontend && npm audit --omit=dev
```

Container images, including the Java libraries inside the backend image (list the images with
`docker compose images`):

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image --scanners vuln --severity HIGH,CRITICAL <image>
```

Dependabot opens pull requests for npm, Gradle, Docker images and GitHub Actions, and GitHub
shows its security alerts under the repository's Security tab.

### 2.3 Lighthouse

Mobile is Lighthouse's default:

```bash
npx lighthouse https://shop.example/shop --only-categories=performance,accessibility,best-practices,seo --view
```

Check Home, Shop, a product, a species, Gallery, About, Contact, Cart, Sign in and a German
page. Scores move a few points between runs, so run each page twice before acting on a small
drop.

### 2.4 Accessibility

- Lighthouse's accessibility score (2.3) runs axe-core on the public pages.
- For pages behind sign-in (account, checkout, admin), the free axe DevTools browser extension
  runs the same rules.
- Keyboard: Tab through each main page. "Skip to content" comes first, every stop has a
  visible ring and is in view, dialogs close with Escape and focus returns to the button that
  opened them.
- Screen reader: see §1.4.
