-- PayPal (docs/DATABASE.md §3.7): a PayPal order (provider_reference) is paid by a capture with
-- its own id. Refund events name only the capture, so it is kept next to the order's id.
-- Stripe payments leave it empty.
ALTER TABLE payments ADD COLUMN capture_reference varchar(100);
CREATE UNIQUE INDEX uq_payments_capture_reference ON payments (capture_reference);
