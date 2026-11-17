# ADR-010: Hosted payment components, webhook-confirmed payments

- **Status:** accepted (card payment through Stripe built; PayPal built 2026-11-17, see the addendum)
- **Date:** 2026-11-13

## Context

The shop sells live animals: stock is reserved when an order is placed and must go back on
sale quickly if nobody pays. Bank transfer (Phase 5) takes days and needs the owner to mark
orders as paid. Customers expect to pay by card, Apple Pay or Google Pay. Card data must
never touch our servers (PCI DSS), and a payment must never be trusted because a browser
says it worked.

## Decision

Use Stripe's Payment Element with PaymentIntents, and let only Stripe's signed webhook mark
an order as paid.

- **Server:** the official `stripe-java` SDK creates one PaymentIntent per order for the
  stored order total (idempotency key `order-<id>-intent`, `allowed_payment_method_types =
  [card]`), and verifies webhook signatures. The account is configured with
  `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` and `STRIPE_WEBHOOK_SECRET` (all three or none,
  never test and live keys mixed — checked at startup).
- **Browser:** `@stripe/stripe-js` (the `pure` entry, so Stripe.js loads only when the payment
  page calls it) and `@stripe/react-stripe-js` render the Payment Element on
  `/checkout/pay/:orderNumber`, a lazy chunk. The publishable key comes from the API.
  `confirmPayment` uses `redirect: "if_required"`, so 3-D Secure normally runs in a Stripe
  window and only some banks redirect.
- **Confirmation:** `payment_intent.succeeded` (amount and currency must match), stored once
  per event id and processed in the same transaction that marks the order paid. The
  confirmation page polls the order for about 30 seconds.
- **Deadlines:** card orders hold their animals for `ORDER_CARD_PAYMENT_MINUTES` (default 60).
  Expiry and customer cancellation cancel the PaymentIntent at Stripe first and keep the order
  if Stripe already has the money.
- **Module boundary:** the payment module defines `PayableOrders`; the order module implements
  it. Locks: order, then payment.
- **Data minimisation:** webhook payloads are reduced to ids, amounts and statuses before they
  are stored; Stripe's Link is switched off in the form.

## Addendum (2026-11-17): PayPal

PayPal follows the same rules — amount from the stored order, one provider payment per order,
the browser never decides that an order is paid — with PayPal's own flow:

- **Server:** PayPal's REST API (Orders v2) called with the JDK's HTTP client and an OAuth 2
  access token (`PayPalApiClient`; no PayPal SDK dependency). The account is configured with
  `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` and `PAYPAL_WEBHOOK_ID` (all three or none) and
  `PAYPAL_ENVIRONMENT` (`sandbox` by default, `live` in production).
- **One PayPal order per shop order**, created for the stored total (`PayPal-Request-Id`
  `order-<id>`), `intent: CAPTURE`, `shipping_preference: NO_SHIPPING` (the shop has the
  address; PayPal neither asks for one nor sends one back), `user_action: PAY_NOW`, the
  customer's language. Reloads and further attempts reuse it.
- **Browser:** PayPal's script (`@paypal/paypal-js` loader, only on the payment page) draws
  PayPal's own button (`fundingSource: paypal`; cards are paid through Stripe, so PayPal's card
  button is switched off). The button opens the PayPal order the server created.
- **Confirmation differs from Stripe:** after the customer approved in PayPal's window, the
  browser asks our server to capture (`POST /payments/paypal/captures`); the server takes the
  money from PayPal (`PayPal-Request-Id` `order-<id>-capture`) and decides from **PayPal's
  answer to the server** whether the order is paid. The browser only reports that the customer
  approved, which by itself moves no money. A declined funding source lets the customer choose
  another in PayPal's window (`actions.restart()`).
- **Webhooks back this up:** `CHECKOUT.ORDER.APPROVED` (captures an approval the browser never
  reported, while the order still waits for payment), `PAYMENT.CAPTURE.COMPLETED`/`PENDING`/
  `DENIED`/`DECLINED` and `PAYMENT.CAPTURE.REFUNDED`/`REVERSED`. PayPal confirms each delivery
  (its verify-webhook-signature API with our webhook id, the body passed on exactly as
  received); deliveries are stored once per event id, reduced to ids, amounts and statuses.
- **Deadlines and cancellation:** PayPal orders share the card deadline
  (`ORDER_CARD_PAYMENT_MINUTES`). A PayPal order cannot be withdrawn, but we never capture for
  a cancelled order, so cancelling marks our payment cancelled — unless PayPal already has the
  money or is still checking it (we ask PayPal first), in which case the order stays.
- **Payment page:** `/checkout/pay/:orderNumber` shows the form for the order's own method, so
  emails and order pages link to one address for both.

Consequences: PayPal's script sets cookies and contacts PayPal hosts on the payment page only
(CSP: `*.paypal.com`, `*.paypalobjects.com`); the privacy policy must name PayPal. Production
needs the webhook registered for the REST app. Refunds are made from the admin order page (see
the refunds addendum); refunds made in the PayPal account are recorded from the refund events.

## Addendum (2026-11-17): Refunds from the admin

The owner refunds card and PayPal payments, all or part, from the admin order page; the shop
asks the provider that took the money (Stripe's Refunds API, PayPal's capture refund).

- **The amount comes from the stored payment:** at most what is left (`amount − refunded`),
  checked under the order's and the payment's locks. The request also carries the refundable
  amount the owner saw; if a refund was made meanwhile (another tab, the provider's dashboard)
  the server answers `REFUNDS_CHANGED` instead of refunding against a stale figure.
- **Never twice:** every request carries an idempotency key, stored with the refund and passed
  on to the provider (`refund-<key>`). A repeat returns the first refund; the admin app keeps
  the key until the server has answered.
- **Counted when accepted:** a refund counts as refunded as soon as the provider accepts it,
  pending ones included, as Stripe and PayPal count them, so the shop's total agrees with the
  provider's refund events. A refusal (or a refund failed at once) rolls the transaction back.
- **Later failures are taken back:** Stripe can fail a refund afterwards (e.g. a closed card
  account). `refund.updated`/`refund.failed` then subtract it again, mark it failed and tell the
  shop inbox; the order's payment status follows (back to paid if nothing is left refunded).
- **Bank transfers** are paid back by the owner's own transfer, outside the shop.

Accepted risk: if the provider's answer is lost after it made the refund (a timeout), nothing
is recorded locally; the provider's refund event still updates the total, and repeating the
request with the same key returns the provider's refund.

## Alternatives considered

- **Stripe Checkout (hosted page):** less code, but the customer leaves the shop, and our own
  order review and confirmations (live-animal conditions, shipping days) would be split from
  the payment step.
- **Confirming payment from the browser redirect:** simpler, but anyone can call a redirect
  URL; a lost redirect would leave a paid order unpaid.
- **PayPal: capturing in the browser (`actions.order.capture()`):** the browser would decide
  that money moved; the server would only learn it from a webhook.
- **PayPal: the official server SDK:** another dependency for four calls the JDK's HTTP client
  makes in a small, tested class.
- **Creating the PaymentIntent before the order exists:** the amount could change between
  payment and order placement, and stock would not be reserved during 3-D Secure.

## Consequences

- Two new dependencies (backend SDK, two small browser libraries limited to one chunk).
- Stripe.js sets cookies and contacts Stripe hosts on the payment page only; the privacy
  policy must name Stripe, and a CSP must allow Stripe's hosts.
- Production needs the webhook endpoint registered in the Stripe Dashboard, and Apple Pay
  needs domain verification. Locally, the Stripe CLI forwards events.
- Refunds are made from the admin order page (addendum above); refunds made in the Stripe
  Dashboard are recorded from `charge.refunded`. The webhook needs `refund.updated` and
  `refund.failed` as well.
