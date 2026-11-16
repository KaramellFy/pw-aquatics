# ADR-012: The language is part of the link

- **Status:** accepted
- **Date:** 2026-11-16

## Context

The shop has English and German texts. Until now the chosen language was kept only in the
visitor's browser (`localStorage`), and every link showed the same page in either language.
Search engines keep no such choice, so they only ever saw the English shop: German pages could
not be found on Google, although the shop is based in Germany and German-speaking customers are
an important market.

The owner decided (2026-11-16): German pages get their own links; the owner writes German product
texts; no additional server in production.

## Decision

- English pages stay at `/…`; German pages are the same pages under `/de/…`, with the **same path
  words** (`/de/products/blue-dream-high`, `/de/shop/neocaridina`). German path words would add
  little for search and a second set of routes and slugs to maintain.
- **The link decides the language.** The router is created with the basename of the page load
  (`/de` or none), so every `<Link>`, `navigate` and redirect stays in that language without the
  pages knowing about it. Components keep writing language-neutral paths (`/shop`).
- The language switch is a pair of real links to the same page in each language (crawlers follow
  them). Following one loads the page in the other language; nothing in memory is lost that the
  server does not keep (cart, session).
- The last choice is still remembered in the browser, but it only redirects the bare home page
  (`/` → `/de/`). Every other link keeps its language, so shared links and search results always
  open as they were linked. Search engines keep no choice, so they always get what they ask for.
- Indexable pages name their canonical link and the same page in both languages (`hreflang`,
  English as `x-default`). Pages that are not indexed (cart, account, checkout, admin) carry no
  alternates; `robots.txt` blocks them in both languages.
- Full links outside the router use the router's `useHref` (e.g. Stripe's return address).
  Emails link to the customer's language through `common.web.SiteLinks` (the user's or the
  order's stored locale).
- The admin area works under both prefixes; `/admin` is English, `/de/admin` German.

## Consequences

- German pages can be indexed once the page HTML carries the language links before JavaScript
  runs (backend-filled pages, roadmap "SEO") and German catalog texts exist.
- Changing the language reloads the page (rare, and it guarantees the server-filled HTML of the
  next step matches the language).
- A third language would add one prefix; the helpers (`languageOfPath`, `pathInLanguage`) and
  `SiteLinks` are the only places that know the prefixes.

## Alternatives considered

- **Language only in the browser (as before):** no German search results.
- **Language as a query parameter (`?lang=de`):** search engines handle it worse than a path,
  and every internal link would need to carry it.
- **Separate German domain (`.de`):** a second domain to buy, certify and maintain; no benefit
  for a single shop.
- **German path words (`/de/produkte/…`):** more routes and translated slugs for little gain.
