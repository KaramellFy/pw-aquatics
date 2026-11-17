import { act, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { PayPalPayment } from '../api/payments';
import { PaymentPage } from './PaymentPage';

// PayPal's script is not loaded in tests: a stand-in records the button's options and lets the
// test play the customer's part in PayPal's window.
interface ButtonOptions {
  fundingSource?: string;
  createOrder: () => Promise<string>;
  onApprove: (data: unknown, actions: { restart: () => void }) => Promise<void>;
  onCancel: () => void;
  onError: (error: unknown) => void;
}
const paypal = vi.hoisted(() => ({
  loadScript: vi.fn(),
  buttons: [] as ButtonOptions[],
  restart: vi.fn(),
}));
vi.mock('@paypal/paypal-js', () => ({ loadScript: paypal.loadScript }));

const namespace = {
  version: 'test',
  FUNDING: { PAYPAL: 'paypal' },
  Buttons: (options: ButtonOptions) => {
    paypal.buttons.push(options);
    return {
      isEligible: () => true,
      render: (container: HTMLElement) => {
        container.append(Object.assign(document.createElement('button'), { textContent: 'PayPal' }));
        return Promise.resolve();
      },
      close: () => Promise.resolve(),
    };
  },
};

const payment: PayPalPayment = {
  orderNumber: 'PW-2026-000077',
  amount: { amount: 1290, currency: 'EUR' },
  paymentDueAt: '2026-11-17T11:30:00Z',
  paypalOrderId: '5O190127TN364715T',
  clientId: 'test-client-id',
  alreadyPaid: false,
};
const order = { orderNumber: 'PW-2026-000077', paymentMethod: 'PAYPAL', status: 'PENDING_PAYMENT' };

const routes = [
  { path: '/checkout/pay/:orderNumber', element: <PaymentPage /> },
  { path: '/checkout/complete/:orderNumber', element: <h1>Order placed</h1> },
  { path: '/account/orders/:orderNumber', element: <h1>Order detail</h1> },
];
const path = '/checkout/pay/PW-2026-000077';

/** The customer approves in PayPal's window; resolves once the page has handled it. */
async function approveInPayPal() {
  const button = paypal.buttons.at(-1)!;
  await act(() => button.onApprove({ orderID: payment.paypalOrderId }, { restart: paypal.restart }));
}

beforeEach(() => {
  paypal.loadScript.mockResolvedValue(namespace);
  paypal.buttons.length = 0;
  paypal.restart.mockReset();
});
afterEach(() => vi.unstubAllGlobals());

describe('PayPal payment page', () => {
  it("opens the server's PayPal order and lets the server take the money", async () => {
    const fetchMock = mockApi({
      '/account/orders/*': order,
      'POST /payments/paypal/orders': payment,
      'POST /payments/paypal/captures': { orderNumber: payment.orderNumber, status: 'PAID' },
    });
    const { router } = renderRoutes(routes, { path });

    expect(await screen.findByRole('button', { name: 'PayPal' })).toBeInTheDocument();
    expect(screen.getByText(/Order PW-2026-000077/)).toHaveTextContent('€12.90');
    expect(lastBody(fetchMock, 'POST', '/payments/paypal/orders')).toEqual({ orderNumber: 'PW-2026-000077' });
    // Only the PayPal button, in the shop's language; cards go through Stripe.
    expect(paypal.loadScript).toHaveBeenCalledWith({
      clientId: 'test-client-id',
      currency: 'EUR',
      intent: 'capture',
      components: 'buttons',
      locale: 'en_GB',
      disableFunding: 'card',
    });
    const button = paypal.buttons[0]!;
    expect(button.fundingSource).toBe('paypal');
    // The button opens the PayPal order the server created for the stored total.
    await expect(button.createOrder()).resolves.toBe('5O190127TN364715T');

    await approveInPayPal();
    expect(lastBody(fetchMock, 'POST', '/payments/paypal/captures')).toEqual({
      orderNumber: 'PW-2026-000077',
    });
    expect(await screen.findByRole('heading', { name: 'Order placed' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/checkout/complete/PW-2026-000077');
  });

  it('lets the customer choose another way to pay when PayPal declines', async () => {
    mockApi({
      '/account/orders/*': order,
      'POST /payments/paypal/orders': payment,
      'POST /payments/paypal/captures': { orderNumber: payment.orderNumber, status: 'DECLINED' },
    });
    renderRoutes(routes, { path });
    await screen.findByRole('button', { name: 'PayPal' });

    await approveInPayPal();
    expect(paypal.restart).toHaveBeenCalledOnce();
    expect(await screen.findByText(/choose another way to pay/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Order placed' })).not.toBeInTheDocument();
  });

  it('says so when the customer closes PayPal, and keeps the button', async () => {
    mockApi({ '/account/orders/*': order, 'POST /payments/paypal/orders': payment });
    renderRoutes(routes, { path });
    await screen.findByRole('button', { name: 'PayPal' });

    act(() => paypal.buttons[0]!.onCancel());
    expect(await screen.findByText(/You closed PayPal before paying/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'PayPal' })).toBeInTheDocument();
  });

  it.each([
    ['PayPal already has the money', { ...payment, paypalOrderId: null, alreadyPaid: true }],
    ['the order is marked as paid', problem(409, 'ORDER_ALREADY_PAID')],
  ])('moves on to the confirmation when %s', async (_case, answer) => {
    mockApi({ '/account/orders/*': order, 'POST /payments/paypal/orders': answer });
    const { router } = renderRoutes(routes, { path });
    expect(await screen.findByRole('heading', { name: 'Order placed' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/checkout/complete/PW-2026-000077');
    expect(paypal.loadScript).not.toHaveBeenCalled();
  });

  it('explains when the order can no longer be paid', async () => {
    mockApi({
      '/account/orders/*': order,
      'POST /payments/paypal/orders': problem(409, 'ORDER_NOT_PAYABLE'),
    });
    renderRoutes(routes, { path });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('This order can no longer be paid');
    expect(within(alert).queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
    expect(paypal.loadScript).not.toHaveBeenCalled();
  });

  it('says so when PayPal cannot be loaded', async () => {
    mockApi({
      '/account/orders/*': order,
      // Another client id, because PayPal's script is loaded only once per id and page visit.
      'POST /payments/paypal/orders': { ...payment, clientId: 'unreachable' },
    });
    paypal.loadScript.mockRejectedValue(new Error('Failed to load the PayPal JS SDK script.'));
    renderRoutes(routes, { path });
    expect(await screen.findByText(/PayPal could not be loaded/)).toBeInTheDocument();
  });

  it('shows the card form for card orders', async () => {
    const fetchMock = mockApi({
      '/account/orders/*': { ...order, paymentMethod: 'STRIPE' },
      'POST /payments/stripe/intents': problem(409, 'ORDER_NOT_PAYABLE'),
    });
    renderRoutes(routes, { path });
    expect(await screen.findByRole('alert')).toHaveTextContent('This order can no longer be paid');
    expect(() => lastBody(fetchMock, 'POST', '/payments/paypal/orders')).toThrow();
    expect(paypal.loadScript).not.toHaveBeenCalled();
  });
});
