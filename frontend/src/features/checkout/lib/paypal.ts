import { loadScript, type PayPalNamespace } from '@paypal/paypal-js';

const loaded = new Map<string, Promise<PayPalNamespace | null>>();

/**
 * PayPal's script for the shop's client id (which comes from the API), loaded once per page
 * visit and only on the payment page. It is served by PayPal (www.paypal.com) as their terms
 * require; the customer signs in and pays in PayPal's own window.
 *
 * Only the PayPal button is used (see `PayPalButton`): cards are paid through Stripe.
 */
export function getPayPal(
  clientId: string,
  currency: string,
  language: string,
): Promise<PayPalNamespace | null> {
  const key = `${clientId}|${currency}|${language}`;
  let paypal = loaded.get(key);
  if (!paypal) {
    paypal = loadScript({
      clientId,
      currency,
      intent: 'capture',
      components: 'buttons',
      locale: payPalLocale(language),
      disableFunding: 'card',
    });
    // A failed download (offline, blocked) may be retried.
    paypal.catch(() => loaded.delete(key));
    loaded.set(key, paypal);
  }
  return paypal;
}

/** PayPal's window shows its texts in the shop's language. */
export function payPalLocale(language: string): string {
  return language === 'de' ? 'de_DE' : 'en_GB';
}
