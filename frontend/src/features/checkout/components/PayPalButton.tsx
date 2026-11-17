import type { PayPalNamespace } from '@paypal/paypal-js';
import { useEffect, useRef } from 'react';

/** What happened after the customer approved: `retry` lets them choose another way to pay in PayPal. */
export type ApprovalOutcome = 'retry' | 'done';

interface PayPalButtonProps {
  paypal: PayPalNamespace;
  /** The PayPal order our server created for the stored order total. */
  paypalOrderId: string;
  /** Called after the customer approved in PayPal's window; our server takes the money. */
  onApprove: () => Promise<ApprovalOutcome>;
  onCancel: () => void;
  onError: () => void;
}

/**
 * PayPal's own button, drawn by PayPal in its frame. It opens the PayPal order the server
 * created (never one made in the browser), so the amount cannot be changed here. Only the
 * PayPal button is shown: cards are paid through Stripe.
 */
export function PayPalButton({ paypal, paypalOrderId, onApprove, onCancel, onError }: PayPalButtonProps) {
  const container = useRef<HTMLDivElement>(null);
  // PayPal's frame keeps the handlers it was given; these refs let it call the current ones.
  const handlers = useRef({ onApprove, onCancel, onError });
  useEffect(() => {
    handlers.current = { onApprove, onCancel, onError };
  });

  useEffect(() => {
    const element = container.current;
    if (!element || !paypal.Buttons) {
      handlers.current.onError();
      return;
    }
    const buttons = paypal.Buttons({
      fundingSource: paypal.FUNDING?.PAYPAL,
      style: { layout: 'vertical', shape: 'rect', color: 'gold', label: 'paypal', height: 48 },
      createOrder: () => Promise.resolve(paypalOrderId),
      onApprove: async (_data, actions) => {
        if ((await handlers.current.onApprove()) === 'retry') {
          actions.restart();
        }
      },
      onCancel: () => handlers.current.onCancel(),
      onError: () => handlers.current.onError(),
    });
    let closed = false;
    if (!buttons.isEligible()) {
      handlers.current.onError();
      return;
    }
    buttons.render(element).catch(() => {
      if (!closed) handlers.current.onError();
    });
    return () => {
      closed = true;
      buttons.close().catch(() => undefined);
    };
  }, [paypal, paypalOrderId]);

  return <div ref={container} className="min-h-12" />;
}
