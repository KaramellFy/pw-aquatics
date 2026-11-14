import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useEffect, type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { CardPayment } from '../api/payments';
import { CardPaymentPage } from './CardPaymentPage';

// Stripe.js is not loaded in tests: its React components are replaced by stand-ins that
// behave like the real ones as far as this page is concerned.
const stripe = vi.hoisted(() => ({
  confirmPayment: vi.fn(),
  loadStripe: vi.fn(),
  elementsOptions: [] as unknown[],
  paymentElementOptions: [] as unknown[],
}));
vi.mock('@stripe/stripe-js/pure', () => ({ loadStripe: stripe.loadStripe }));
vi.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ children, options }: { children: ReactNode; options: unknown }) => {
    stripe.elementsOptions.push(options);
    return <>{children}</>;
  },
  PaymentElement: ({ onReady, options }: { onReady: () => void; options: unknown }) => {
    stripe.paymentElementOptions.push(options);
    useEffect(() => onReady(), [onReady]);
    return <div data-testid="payment-element" />;
  },
  useStripe: () => ({ confirmPayment: stripe.confirmPayment }),
  useElements: () => ({}),
}));

const payment: CardPayment = {
  orderNumber: 'PW-2026-000042',
  amount: { amount: 1770, currency: 'EUR' },
  paymentDueAt: '2026-11-13T11:30:00Z',
  clientSecret: 'pi_123_secret_456',
  publishableKey: 'pk_test_123',
  alreadyPaid: false,
};

const order = {
  orderNumber: 'PW-2026-000042',
  billingAddress: { firstName: 'Anna', lastName: 'Smith', countryCode: 'GB', postalCode: 'SW1A 1AA' },
};

const routes = [
  { path: '/checkout/pay/:orderNumber', element: <CardPaymentPage /> },
  { path: '/checkout/complete/:orderNumber', element: <h1>Order placed</h1> },
  { path: '/account/orders/:orderNumber', element: <h1>Order detail</h1> },
];
const path = '/checkout/pay/PW-2026-000042';

beforeEach(() => {
  stripe.loadStripe.mockResolvedValue({});
  stripe.confirmPayment.mockReset();
  stripe.elementsOptions.length = 0;
  stripe.paymentElementOptions.length = 0;
});
afterEach(() => vi.unstubAllGlobals());

describe('CardPaymentPage', () => {
  it('asks the server for the payment with the order number only and pays through Stripe', async () => {
    const fetchMock = mockApi({ 'POST /payments/stripe/intents': payment, '/account/orders/*': order });
    stripe.confirmPayment.mockResolvedValue({ paymentIntent: { status: 'succeeded' } });
    const { router } = renderRoutes(routes, { path });

    const pay = await screen.findByRole('button', { name: 'Pay €17.70' });
    expect(lastBody(fetchMock, 'POST', '/payments/stripe/intents')).toEqual({
      orderNumber: 'PW-2026-000042',
    });
    expect(screen.getByText(/Order PW-2026-000042/)).toHaveTextContent('€17.70');
    expect(stripe.loadStripe).toHaveBeenCalledWith('pk_test_123');
    expect(stripe.elementsOptions[0]).toMatchObject({ clientSecret: 'pi_123_secret_456', locale: 'en' });
    // The billing country is filled in; Stripe's Link sign-up is not offered.
    expect(stripe.paymentElementOptions[0]).toEqual({
      wallets: { link: 'never' },
      defaultValues: { billingDetails: { address: { country: 'GB', postal_code: 'SW1A 1AA' } } },
    });

    await userEvent.click(pay);
    expect(await screen.findByRole('heading', { name: 'Order placed' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/checkout/complete/PW-2026-000042');
    expect(stripe.confirmPayment).toHaveBeenCalledWith({
      elements: {},
      confirmParams: { return_url: 'http://localhost:3000/checkout/complete/PW-2026-000042' },
      redirect: 'if_required',
    });
  });

  it("shows Stripe's reason for a declined card and lets the customer try again", async () => {
    mockApi({ 'POST /payments/stripe/intents': payment });
    stripe.confirmPayment.mockResolvedValueOnce({
      error: { type: 'card_error', message: 'Your card has insufficient funds.' },
    });
    stripe.confirmPayment.mockResolvedValueOnce({ paymentIntent: { status: 'processing' } });
    renderRoutes(routes, { path });

    await userEvent.click(await screen.findByRole('button', { name: 'Pay €17.70' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Your card has insufficient funds.');
    expect(screen.getByRole('button', { name: 'Pay €17.70' })).not.toHaveAttribute('aria-busy', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Pay €17.70' }));
    expect(await screen.findByRole('heading', { name: 'Order placed' })).toBeInTheDocument();
  });

  it.each([
    ['Stripe already has the money', { ...payment, clientSecret: null, alreadyPaid: true }],
    ['the order is marked as paid', problem(409, 'ORDER_ALREADY_PAID')],
  ])('moves on to the confirmation when %s', async (_case, answer) => {
    mockApi({ 'POST /payments/stripe/intents': answer });
    const { router } = renderRoutes(routes, { path });
    expect(await screen.findByRole('heading', { name: 'Order placed' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/checkout/complete/PW-2026-000042');
    expect(stripe.loadStripe).not.toHaveBeenCalled();
  });

  it('explains when the order can no longer be paid', async () => {
    mockApi({ 'POST /payments/stripe/intents': problem(409, 'ORDER_NOT_PAYABLE') });
    renderRoutes(routes, { path });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('This order can no longer be paid');
    expect(within(alert).queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
    await userEvent.click(within(alert).getByRole('link', { name: 'View order' }));
    expect(await screen.findByRole('heading', { name: 'Order detail' })).toBeInTheDocument();
    expect(stripe.loadStripe).not.toHaveBeenCalled();
  });

  it('says so when Stripe cannot be loaded', async () => {
    // Another key, because Stripe.js is loaded only once per key and page visit.
    mockApi({ 'POST /payments/stripe/intents': { ...payment, publishableKey: 'pk_test_unreachable' } });
    stripe.loadStripe.mockRejectedValue(new Error('Failed to load Stripe.js'));
    renderRoutes(routes, { path });
    expect(await screen.findByText(/payment form could not be loaded/)).toBeInTheDocument();
  });
});
