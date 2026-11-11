import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { Order, OrderSummary } from '../api/orders';
import { OrderDetailPage } from './OrderDetailPage';
import { OrdersPage } from './OrdersPage';

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

const order: Order = {
  orderNumber: 'PW-2026-000042',
  status: 'PENDING_PAYMENT',
  paymentStatus: 'UNPAID',
  paymentMethod: 'BANK_TRANSFER',
  placedAt: '2026-11-10T10:00:00Z',
  // One second before midnight in Berlin: the deadline is the 17th, in every visitor's time zone.
  paymentDueAt: '2026-11-17T22:59:59Z',
  plannedDispatchDate: '2026-11-16',
  items: [
    {
      sku: 'BD-1',
      productName: 'Blue Dream',
      variantName: '1 animal',
      speciesName: 'Neocaridina davidi',
      gradeName: 'High',
      quantity: 2,
      unitPrice: money(390),
      lineTotal: money(780),
    },
  ],
  totals: { subtotal: money(780), shipping: money(990), total: money(1770), includedTax: money(283) },
  shippingMethodName: 'DHL Express – Germany',
  shippingCarrier: 'DHL',
  shippingAddress: address,
  billingAddress: { ...address, city: 'Hamburg' },
  customerNote: 'Ring twice',
  containsLiveAnimals: true,
  statusTimeline: [{ status: 'PENDING_PAYMENT', at: '2026-11-10T10:00:00Z', message: null }],
  paymentInstructions: {
    type: 'BANK_TRANSFER',
    accountHolder: 'PW Aquatics',
    iban: 'DE00 0000 0000 0000 0000 00',
    bic: 'TESTDEFFXXX',
    bankName: null,
    reference: 'PW-2026-000042',
    amount: money(1770),
  },
  cancellable: true,
};
const cancelled: Order = {
  ...order,
  status: 'CANCELLED',
  paymentDueAt: null,
  paymentInstructions: null,
  cancellable: false,
  statusTimeline: [
    ...order.statusTimeline,
    { status: 'CANCELLED', at: '2026-11-10T11:00:00Z', message: null },
  ],
};

const summary: OrderSummary = {
  orderNumber: order.orderNumber,
  status: 'PENDING_PAYMENT',
  paymentStatus: 'UNPAID',
  placedAt: order.placedAt,
  total: money(1770),
  itemCount: 2,
  productNames: ['Blue Dream'],
};
const pageOf = (items: OrderSummary[], totalPages = 1) => ({
  items,
  page: 0,
  size: 10,
  totalItems: items.length,
  totalPages,
});

const routes = [
  { path: '/account/orders', element: <OrdersPage /> },
  { path: '/account/orders/:orderNumber', element: <OrderDetailPage /> },
];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('OrdersPage', () => {
  it('lists orders with status and total and links to the detail page', async () => {
    mockApi({
      '/account/orders': pageOf([summary, { ...summary, orderNumber: 'PW-2026-000007', status: 'SHIPPED' }]),
      '/account/orders/PW-2026-000042': order,
    });
    renderRoutes(routes, { path: '/account/orders' });

    const row = (await screen.findByRole('heading', { name: 'Order PW-2026-000042' })).closest('li')!;
    expect(within(row).getByText('Awaiting payment')).toBeInTheDocument();
    expect(within(row).getByText('Ordered on 10 November 2026')).toBeInTheDocument();
    expect(within(row).getByText('Blue Dream')).toBeInTheDocument();
    expect(within(row).getByText('€17.70')).toBeInTheDocument();
    expect(screen.getByText('Shipped')).toBeInTheDocument();

    await userEvent.click(within(row).getByRole('link', { name: 'View order PW-2026-000042' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Order PW-2026-000042' }),
    ).toBeInTheDocument();
  });

  it('invites to the shop when nothing has been ordered yet', async () => {
    mockApi({ '/account/orders': pageOf([]) });
    renderRoutes(routes, { path: '/account/orders' });

    expect(await screen.findByRole('heading', { name: 'No orders yet' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Browse the shop' })).toHaveAttribute('href', '/shop');
  });
});

describe('OrderDetailPage', () => {
  it('shows the order, the bank details and the payment deadline as the server sent them', async () => {
    mockApi({ '/account/orders/PW-2026-000042': order });
    renderRoutes(routes, { path: '/account/orders/PW-2026-000042' });

    const payment = await screen.findByRole('region', { name: 'Pay by bank transfer' });
    expect(within(payment).getByText(/Please transfer the total by 17 November 2026/)).toBeInTheDocument();
    expect(within(payment).getByText('DE00 0000 0000 0000 0000 00')).toBeInTheDocument();
    expect(within(payment).getByText('PW-2026-000042')).toBeInTheDocument();
    expect(within(payment).getByText('€17.70')).toBeInTheDocument();

    expect(screen.getByText('Quantity: 2 · €3.90 each')).toBeInTheDocument();
    expect(screen.getByText('€9.90')).toBeInTheDocument();
    expect(screen.getByText('incl. €2.83 VAT')).toBeInTheDocument();
    expect(screen.getByText('Earliest dispatch: Monday, 16 November 2026')).toBeInTheDocument();
    expect(screen.getByText('10115 Hamburg')).toBeInTheDocument();
    expect(screen.getByText('Ring twice')).toBeInTheDocument();
  });

  it('cancels an order after confirmation and then offers neither cancelling nor bank details', async () => {
    const fetchMock = mockApi({
      '/account/orders/PW-2026-000042': order,
      'POST /account/orders/PW-2026-000042/cancel': cancelled,
    });
    renderRoutes(routes, { path: '/account/orders/PW-2026-000042' });

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const dialog = screen.getByRole('dialog', { name: 'Cancel this order?' });
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));

    expect(await screen.findAllByText('Cancelled')).not.toHaveLength(0);
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Pay by bank transfer' })).not.toBeInTheDocument();
  });

  it('does not offer cancelling when the server says it is no longer possible', async () => {
    mockApi({ '/account/orders/PW-2026-000042': { ...order, status: 'PAID', cancellable: false } });
    renderRoutes(routes, { path: '/account/orders/PW-2026-000042' });

    expect(await screen.findByText('Payment received')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('treats unknown and foreign orders alike', async () => {
    mockApi({ '/account/orders/PW-2026-999999': problem(404, 'NOT_FOUND') });
    renderRoutes(routes, { path: '/account/orders/PW-2026-999999' });

    expect(await screen.findByRole('heading', { name: 'Order not found' })).toBeInTheDocument();
  });
});
