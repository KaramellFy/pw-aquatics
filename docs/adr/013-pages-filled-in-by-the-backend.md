# ADR-013: Public pages are filled in by the backend

- **Status:** accepted
- **Date:** 2026-11-16

## Context

The shop is a single-page app: every link used to answer with the same empty `index.html`, and
the page only existed once JavaScript had run. Google renders JavaScript, but later and not
always; other search engines and link previews (WhatsApp, Facebook, Slack) mostly do not. They
saw "PW Aquatics" and an English description on every page, including the German ones
(ADR-012), and unknown products answered `200` (a "soft 404").

The owner decided (2026-11-16) not to run an additional server in production, so a Node.js
renderer (server-side rendering, a prerender service) was ruled out. Prerendering at build time
was dropped earlier: prices, stock and products change daily through the admin.

## Decision

- **Caddy asks the backend for pages.** The frontend's Caddy sends every request for a page (a
  path without a file extension) to the backend as `GET /_pages/<path>` and passes the answer
  on. Scripts, styles, photos and other files are served as before. If the backend answers 5xx
  or cannot be reached, Caddy serves the plain `index.html`, so the shop never depends on this.
- **The template is the frontend build.** The backend loads `index.html` and `pages.json`
  from the frontend container (`app.pages.shell-url`) and checks them again at most once a second
  with a conditional request, so a page never points at the scripts of an older build. Without
  `app.pages.shell-url` (local development with Vite) nothing changes.
- **What the backend fills in** for the home page, shop and category pages, products, species,
  gallery, about and contact, in English and German:
  - `<html lang>`; title, description, canonical link and `hreflang` alternates, `noindex` where
    the app's `PageMeta` sets it (a German page whose texts are not translated), Open Graph tags
    (with the product photo) and the product's structured data;
  - the page's main text inside the app's root element: headings, breadcrumbs, product lists with
    links and prices, product and species descriptions, gallery photos;
  - the API responses the app requests first (`<script type="application/json" id="page-data">`),
    keyed by their API path.
  Unknown products, species and categories answer `404`, and so does every address the app has no
  page for (the app's route list is in `pages.json`). The app's other pages (account, cart,
  checkout, admin, legal pages) get the template with only the language set. Caddy answers a
  missing file (an address with an extension) with `404` and the app's "not found" page.
- **Interface texts and routes come from the frontend.** The build writes `pages.json` with the
  few texts the backend needs (page titles and descriptions, navigation, labels) from the app's
  translations (`lib/seo/pageTexts.ts`) and the app's route list (`app/routePaths.ts`, which a
  test keeps equal to the route tree). Owner-written texts come from the database as in the API.
  There is one source for every word and every route.
- **The app takes over.** On start it puts `page-data` into the query cache, removes the backend's
  head tags (its own `PageMeta` writes them from then on) and waits until the router has loaded
  the page's code before it renders. The backend's text stays visible until the app renders the
  same page over it, without a loading state in between. It is not hydrated: the backend's HTML
  only has to be correct and readable (a small stylesheet, `.page-preview`), not identical.
- Pages are the same for every visitor: Caddy sends no cookies to `/_pages` and passes none back.

## Consequences

- Every public page has its title, description, language links, structured data and text in the
  first response; German pages are German before any JavaScript runs; link previews work.
- Unknown addresses, products, species and categories are real `404`s (no "soft 404s"); the app
  still shows its "not found" page there.
- One more request per page view inside the deployment (backend → frontend container, usually a
  `304`). Page views run the same queries as the API calls they replace.
- The backend knows a few frontend details: the template's markers (`frontend/index.html` says
  so), the API paths and page sizes the app requests first (`PageService` constants), and the
  `.page-preview` class names. A mismatch only costs an extra API request or a plainer first
  paint, never a broken page; `PageApiTest` and the frontend tests cover the contract.

## Alternatives considered

- **Node.js server-side rendering or a prerender service:** another server in production
  (owner decision against it).
- **Prerendering at build time:** stale prices and stock; new products only after a deploy.
- **Only head tags, no text:** cheaper, but crawlers without JavaScript would still see an empty
  page and no links to the products.
- **Hydrating the backend's HTML:** would require rendering React on the server.
