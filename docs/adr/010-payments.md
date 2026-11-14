# ADR-010: Hosted payment components, webhook-confirmed payments

- **Status:** accepted (card payment through Stripe built; PayPal follows the same pattern)
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

## Alternatives considered

- **Stripe Checkout (hosted page):** less code, but the customer leaves the shop, and our own
  order review and confirmations (live-animal conditions, shipping days) would be split from
  the payment step.
- **Confirming payment from the browser redirect:** simpler, but anyone can call a redirect
  URL; a lost redirect would leave a paid order unpaid.
- **Creating the PaymentIntent before the order exists:** the amount could change between
  payment and order placement, and stock would not be reserved during 3-D Secure.

## Consequences

- Two new dependencies (backend SDK, two small browser libraries limited to one chunk).
- Stripe.js sets cookies and contacts Stripe hosts on the payment page only; the privacy
  policy must name Stripe, and a CSP must allow Stripe's hosts.
- Production needs the webhook endpoint registered in the Stripe Dashboard, and Apple Pay
  needs domain verification. Locally, the Stripe CLI forwards events.
- Refunds are made in the Stripe Dashboard; the shop records them from `charge.refunded`.
