# ADR-014: Sign in with Google

- **Status:** accepted (Google built 2026-11-17; Apple can follow the same way)
- **Date:** 2026-11-17

## Context

The owner asked for "Sign in with Google / Apple". Apple needs a paid Apple Developer account and
only works on the shop's real HTTPS domain, so the owner chose Google first (2026-11-17). Logins so
far are email + password with server-side sessions (ADR-002); a sign-in through Google must end in
the same kind of session and must not weaken the account rules (verified address, locks, no account
enumeration, no takeover).

The owner decided that a Google sign-in with the address of an existing account connects to that
account automatically, as long as Google has verified the address.

## Decision

- **OpenID Connect, authorization code flow with PKCE**, run by the server. The browser opens
  `GET /api/v1/auth/google` as a page; the server sends it to Google and handles Google's redirect to
  `GET /api/v1/auth/google/callback`. The shop's pages load nothing from Google (no Google script,
  no CSP change).
- **Spring Security's OAuth 2 client parts, not its `oauth2Login()` filter:** the authorization
  request (random `state`, `nonce`, PKCE S256) from `DefaultOAuth2AuthorizationRequestResolver`,
  kept in the session (`HttpSessionOAuth2AuthorizationRequestRepository`), the code exchange and
  ID-token check from `OidcAuthorizationCodeAuthenticationProvider` (signature against Google's
  keys, issuer, audience, expiry, nonce). They are called from an ordinary controller
  (`GoogleSignInController` with `GoogleAuthorizationFlow`), so the session is started by
  `SessionManager` as for a password login, errors become friendly redirects, and the rest of the
  security configuration is untouched. New dependencies: `spring-security-oauth2-client` and
  `spring-security-oauth2-jose` (versions from Spring Boot). Google's user-info endpoint is not
  called and Google's access token is not kept: the ID token has everything needed.
- **Accounts:** a Google account is linked by Google's subject id in `user_identities` (one per shop
  account). First sign-in: connect to the account with the same address **only if
  `email_verified` is true**, and email the customer that Google sign-in was added; otherwise create
  a verified account without password. If the existing account's address was never confirmed, its
  password is removed before connecting (a stranger may have registered the address: the
  "pre-account takeover"). Administrators sign in with their password only. Locked accounts stay
  locked.
- **Accounts without a password:** changing the email address and deleting the account need either
  the password or a confirmation with the connected Google account (`intent=confirm`), which counts
  for 10 minutes and one change. A password is set through the "forgot password" email. Google can
  be disconnected only while the account has a password.
- **Configuration:** `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (both or neither). Without them
  the button is not shown (`GET /auth/sign-in-options`) and `/auth/google` answers `404`. The
  redirect URI registered at Google is `<APP_BASE_URL>/api/v1/auth/google/callback`.

## Consequences

- Tests run against a local stand-in for Google (`support/FakeGoogle`) that signs real RS256 ID
  tokens and checks the client credentials, redirect URI and PKCE; forged answers (other audience,
  nonce, issuer or key, expired) are tested to fail. A real Google sign-in has to be tried with the
  owner's OAuth client.
- The privacy policy must name Google as the sign-in provider the customer chose; `user_identities`
  is listed in docs/DATABASE.md §6 and is deleted with the account.
- Apple can be added as a second `IdentityProvider` with the same flow; it additionally needs a
  `client_secret` signed with the Apple key and `response_mode=form_post`, which does not carry the
  `SameSite=Lax` session cookie, so the authorization request would need another place than the
  session.

## Alternatives considered

- **Spring Security `oauth2Login()`:** adds its own login filters, entry point and default login
  page next to our JSON API, stores the access token in the session, and makes friendly error
  redirects and the confirm-with-Google case harder.
- **Google Identity Services in the browser (One Tap / its JS button):** loads Google's script on the
  sign-in pages (CSP, privacy) and hands an ID token to the browser first.
- **Hand-written OIDC client:** token validation is security-critical; the library is maintained
  and tested.
- **Linking accounts only after typing the password:** safer against unusual cases but an extra
  step; the owner chose automatic linking for verified addresses.
