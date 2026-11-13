import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Order } from '@/features/account';
import { mockApi } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import { OrderConfirmationPage } from './OrderConfirmationPage';

const money = (amount: number) => ({ amount, currency: 'EUR' });
const address = {
  firstName: 'Anna',
  lastName: 'Schmidt',
  company: null,
  street: 'Hauptstraße',
  houseNumber: '12a',
  addressLine2: null,
  postalCode: '10115',
  city: 'Berlin',
  countryCode: 'DE',
  phone: null,
};
const awaitingCard: Order = {
  orderNumber: 'PW-2026-000042',
  status: 'PENDING_PAYMENT',
  paymentStatus: 'UNPAID',
  paymentMethod: 'STRIPE',
  placedAt: '2026-11-13T10:00:00Z',
  paymentDueAt: '2026-11-13T11:00:00Z',
  plannedDispatchDate: '2026-11-16',
  items: [],
  totals: { subtotal: money(780), shipping: money(990), total: money(1770), includedTax: money(283) },
  shippingMethodName: 'DHL Express – Germany',
  shippingCarrier: 'DHL',
  shippingAddress: address,
  billingAddress: address,
  customerNote: null,
  containsLiveAnimals: true,
  statusTimeline: [],
  paymentInstructions: null,
  cancellable: true,
  payableByCard: true,
};
const paid: Order = {
  ...awaitingCard,
  status: 'PAID',
  paymentStatus: 'PAID',
  cancellable: false,
  payableByCard: false,
};

const routes = [{ path: '/checkout/complete/:orderNumber', element: <OrderConfirmationPage /> }];

afterEach(() => vi.unstubAllGlobals());

describe('OrderConfirmationPage after a card payment', () => {
  it('waits until the server has Stripe’s confirmation', async () => {
    let checks = 0;
    mockApi({ '/account/orders/PW-2026-000042': () => (++checks < 2 ? awaitingCard : paid) });
    renderRoutes(routes, { path: '/checkout/complete/PW-2026-000042' });

    expect(await screen.findByText(/confirming your card payment/)).toBeInTheDocument();
    expect(await screen.findByText(/Payment received/, {}, { timeout: 4000 })).toBeInTheDocument();
    expect(checks).toBe(2);
    expect(screen.queryByText(/Transfer the total/)).not.toBeInTheDocument();
  });

  it('offers another attempt when the bank declined after 3-D Secure', async () => {
    mockApi({ '/account/orders/PW-2026-000042': awaitingCard });
    renderRoutes(routes, {
      path: '/checkout/complete/PW-2026-000042?payment_intent=pi_123&redirect_status=failed',
    });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('did not go through');
    expect(within(alert).getByRole('link', { name: 'Try again' })).toHaveAttribute(
      'href',
      '/checkout/pay/PW-2026-000042',
    );
  });
});
