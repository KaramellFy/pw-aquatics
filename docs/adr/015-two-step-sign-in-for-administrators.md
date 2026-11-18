# ADR-015: Two-step sign-in for administrators

- **Status:** accepted
- **Date:** 2026-11-18

## Context

Whoever signs in as an administrator can change prices, refund payments, read every customer's
orders and addresses and lock accounts. Until now a password was enough. Passwords leak (reused
elsewhere, phished, typed on a shared computer), so the owner asked for two-factor sign-in for
admin accounts as part of the production readiness work (2026-11-18). Customers keep signing in
with one step (password or Google, ADR-014).

## Decision

- **TOTP (RFC 6238)** from any authenticator app: HMAC-SHA1, six digits, a new code every
  30 seconds, a code accepted for its own step and one either side. It works offline, costs
  nothing per sign-in and needs no phone number. Implemented in the shop (`identity.domain.Totp`,
  `common.crypto.Base32`, about 100 lines with the RFC's test vectors) instead of adding a
  library for it.
- **Required for the admin area.** A session gets the `ADMIN` role (and the authority
  `FACTOR_TOTP`) only after both steps (`SessionManager.login(…, secondStepPassed)`). The URL rule
  for `/api/v1/admin/**` asks for both, and because the role itself is missing, every
  `@PreAuthorize("hasRole('ADMIN')")` needs the second step too, without touching the admin
  services. An administrator without two-step sign-in is signed in like a customer, sees
  `twoFactorSetupRequired` and is sent to the setup page by the app. The migration signed out all
  administrators once.
- **Sign-in:** after the right password, an account with two-step sign-in is **not** signed in.
  The session (new id; any earlier sign-in in it ended) remembers the pending sign-in for
  5 minutes and 5 wrong codes; `POST /auth/login/two-factor` finishes it with a new session id.
  Too many wrong codes drop it and email the administrator, because whoever tried knew the
  password. Code checks are rate limited per account (5 per 15 minutes) in every place they
  happen.
- **Replay:** the step of the last accepted code is stored; a code is accepted only for a newer
  step, under a row lock, so the same code cannot be used twice, not even by two requests at once.
- **The key** is shown once (QR code drawn in the browser from an `otpauth://` link, plus the
  Base32 text) and stored encrypted with AES-256-GCM under `APP_ENCRYPTION_KEY`, with the user id
  as associated data (`common.crypto.SecretBox`). A copy of the database or a backup alone does
  not reveal it. A new key stays "pending" until its first code arrives, so moving to a new
  phone never breaks the old one halfway. Replacing a working key needs a current code.
- **Recovery codes:** ten one-time codes of 60 random bits, stored as SHA-256 hashes, shown once
  after setup and renewable with a current code. Using one is announced by email with the number
  left. If the encryption key is lost or changed, recovery codes still work and the administrator
  sets up the app again.
- **No "turn off" button.** An administrator who lost phone and recovery codes is reset by the
  server's operator with one SQL statement (docs/OPERATIONS.md), then sets it up again.
- **QR code:** drawn as SVG in the browser by `uqr` (MIT, no dependencies, loaded only on the
  setup page), so the key never goes to a QR service and the CSP stays unchanged.

## Consequences

- `APP_ENCRYPTION_KEY` is a new required setting in production (32 random bytes, Base64); dev and
  test profiles have fixed keys of their own. It belongs in the password manager with the other
  secrets: without it, administrators must use a recovery code and set up the app again.
- Tests sign administrators in through the real endpoints (`AuthFixture.adminSession` sets up an
  app and computes its codes).
- Possible later: passkeys (WebAuthn) as a second factor or instead of passwords; two-step
  sign-in for customers who ask for it; an "administrators" page where one admin can reset
  another's second step.

## Alternatives considered

- **Spring Security 7's multi-factor support** (`@EnableMultiFactorAuthentication`, factor
  authorities): built around its own login filters and redirects, which this API does not use
  (ADR-002, ADR-014). The idea is the same — admin rights need a second factor — done in
  `SessionManager` where sessions already start.
- **Codes by email or SMS:** email is often the same account an attacker took over; SMS costs
  money per sign-in, needs a provider and phone numbers, and can be redirected (SIM swap).
- **A TOTP library** (e.g. `java-otp`, `dev.samstevens.totp`): small value for a short,
  well-specified algorithm that is covered by the RFC's own test vectors.
- **Storing the key as plain text** (as many apps do): a leaked database or backup would let an
  attacker make codes. Hashing is not possible (the server must compute codes), so it is
  encrypted.
