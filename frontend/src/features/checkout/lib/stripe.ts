import type { Appearance, Stripe, StripeElementLocale } from '@stripe/stripe-js';
// "pure": Stripe.js is only downloaded when loadStripe is called, i.e. on the payment page.
import { loadStripe } from '@stripe/stripe-js/pure';

const loaded = new Map<string, Promise<Stripe | null>>();

/**
 * Stripe.js for the shop's publishable key (which comes from the API), loaded once per page
 * visit. The script is served by Stripe (js.stripe.com) as their terms require; card details
 * are typed into Stripe's own fields and never reach our servers.
 */
export function getStripe(publishableKey: string): Promise<Stripe | null> {
  let stripe = loaded.get(publishableKey);
  if (!stripe) {
    stripe = loadStripe(publishableKey);
    // A failed download (offline, blocked) may be retried.
    stripe.catch(() => loaded.delete(publishableKey));
    loaded.set(publishableKey, stripe);
  }
  return stripe;
}

/** Stripe's form shows its texts in the shop's language. */
export function stripeLocale(language: string): StripeElementLocale {
  return language === 'de' ? 'de' : 'en';
}

/**
 * The payment form in the shop's colours. Stripe draws it in its own frame, so it gets the
 * values of our design tokens rather than CSS variables.
 */
export function stripeAppearance(): Appearance {
  const css = getComputedStyle(document.documentElement);
  const token = (name: string, fallback?: string) => css.getPropertyValue(name).trim() || fallback;
  return {
    theme: 'stripe',
    variables: {
      colorPrimary: token('--color-brand-700'),
      colorText: token('--color-ink'),
      colorTextSecondary: token('--color-ink-soft'),
      colorDanger: token('--color-danger'),
      colorBackground: token('--color-surface'),
      // Our web font is not available inside Stripe's frame; the system fonts in the stack are.
      fontFamily: token('--font-sans', 'system-ui, sans-serif'),
      fontSizeBase: '16px',
      borderRadius: '12px',
      spacingUnit: '4px',
    },
    rules: {
      '.Input': { borderColor: token('--color-line-strong') ?? '', boxShadow: 'none' },
      '.Input:focus': { borderColor: token('--color-brand-700') ?? '' },
      '.Label': { fontWeight: '500' },
    },
  };
}
