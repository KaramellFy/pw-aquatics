import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { RequireAdmin } from '@/app/guards/RequireAdmin';
import { AdminLayout } from '@/app/layouts/AdminLayout';
import { anna } from '@/features/auth/test/fixtures';
import type { Session } from '@/features/auth/api/auth';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { AdminOrder, AdminOrderSummary } from '../api/orders';
import { AdminOrderDetailPage } from './AdminOrderDetailPage';
import { AdminOrdersPage } from './AdminOrdersPage';
import { AdminPackingSlipPage } from './AdminPackingSlipPage';

const owner: Session = { authenticated: true, user: { ...anna.user!, role: 'ADMIN' } };
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
const noActions = {
  markPaid: false,
  startProcessing: false,
  ship: false,
  markDelivered: false,
  cancel: false,
  removeShipment: false,
};

const placed: AdminOrder = {
  order: {
    orderNumber: 'PW-2026-000042',
    status: 'PENDING_PAYMENT',
    paymentStatus: 'UNPAID',
    paymentMethod: 'BANK_TRANSFER',
    placedAt: '2026-11-10T10:00:00Z',
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
    billingAddress: address,
    customerNote: 'Please ring twice',
    containsLiveAnimals: true,
    statusTimeline: [{ status: 'PENDING_PAYMENT', at: '2026-11-10T10:00:00Z', message: null }],
    paymentInstructions: null,
    cancellable: true,
    payableByCard: false,
    shipments: [],
  },
  customerId: 'u1',
  customerEmail: 'anna@example.com',
  customerLocale: 'de',
  paidAt: null,
  cancelledAt: null,
  cancellationReason: null,
  history: [
    {
      from: null,
      to: 'PENDING_PAYMENT',
      at: '2026-11-10T10:00:00Z',
      note: null,
      customerMessage: null,
      changedBy: 'Anna Schmidt',
    },
  ],
  notes: [],
  payment: null,
  carriers: ['DHL', 'DHL Express'],
  actions: { ...noActions, markPaid: true, cancel: true },
};

const paid: AdminOrder = {
  ...placed,
  order: { ...placed.order, status: 'PAID', paymentStatus: 'PAID', paymentDueAt: null },
  paidAt: '2026-11-11T09:00:00Z',
  history: [
    ...placed.history,
    {
      from: 'PENDING_PAYMENT',
      to: 'PAID',
      at: '2026-11-11T09:00:00Z',
      note: null,
      customerMessage: null,
      changedBy: 'Phil',
    },
  ],
  actions: { ...noActions, startProcessing: true, ship: true, cancel: true },
};

const shipped: AdminOrder = {
  ...paid,
  order: {
    ...paid.order,
    status: 'SHIPPED',
    cancellable: false,
    shipments: [
      {
        id: 's1',
        carrier: 'DHL',
        trackingNumber: 'JJD000123456789',
        trackingUrl: 'https://www.dhl.com/tracking?tracking-id=JJD000123456789',
        shippedAt: '2026-11-16T09:30:00Z',
        deliveredAt: null,
      },
    ],
  },
  actions: { ...noActions, ship: true, markDelivered: true },
};

const summary: AdminOrderSummary = {
  orderNumber: 'PW-2026-000042',
  placedAt: '2026-11-10T10:00:00Z',
  status: 'PAID',
  paymentStatus: 'PAID',
  paymentMethod: 'BANK_TRANSFER',
  total: money(1770),
  customerName: 'Anna Schmidt',
  customerEmail: 'anna@example.com',
  countryCode: 'DE',
  itemCount: 2,
  containsLiveAnimals: true,
  plannedDispatchDate: '2026-11-16',
  paymentDueAt: null,
};

const routes = [
  {
    path: '/admin',
    element: <RequireAdmin />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          { path: 'orders', element: <AdminOrdersPage /> },
          { path: 'orders/:orderNumber', element: <AdminOrderDetailPage /> },
        ],
      },
      { path: 'orders/:orderNumber/packing-slip', element: <AdminPackingSlipPage /> },
    ],
  },
];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('order list', () => {
  it('asks the server for the "to ship" queue, oldest first', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/orders': { items: [summary], page: 0, size: 25, totalItems: 1, totalPages: 1 },
    });
    renderRoutes(routes, { path: '/admin/orders' });

    const row = (await screen.findByRole('link', { name: 'PW-2026-000042' })).closest('tr')!;
    expect(within(row).getByText('Anna Schmidt')).toBeInTheDocument();
    expect(within(row).getByText('Live animals')).toBeInTheDocument();
    expect(within(row).getByText('€17.70')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'To ship' }));
    await waitFor(() => {
      const url = new URL(String(fetchMock.mock.calls.at(-1)![0]), 'http://localhost');
      expect(url.searchParams.getAll('status')).toEqual(['PAID', 'PROCESSING']);
      expect(url.searchParams.get('oldestFirst')).toBe('true');
    });
    expect(screen.getByRole('button', { name: 'To ship' })).toHaveAttribute('aria-pressed', 'true');
  });
});

describe('order detail', () => {
  it('offers only the steps the server allows and records a bank transfer', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/orders/PW-2026-000042': placed,
      'POST /admin/orders/PW-2026-000042/mark-paid': paid,
    });
    renderRoutes(routes, { path: '/admin/orders/PW-2026-000042' });

    const nextStep = await screen.findByRole('region', { name: 'Next step' });
    expect(within(nextStep).queryByRole('button', { name: 'Enter parcel and ship' })).not.toBeInTheDocument();
    await userEvent.click(within(nextStep).getByRole('button', { name: 'Mark as paid' }));

    const dialog = await screen.findByRole('dialog', { name: 'Mark as paid' });
    expect(dialog).toHaveTextContent('€17.70');
    await userEvent.type(within(dialog).getByLabelText(/Internal note/), 'Found on the account');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark as paid' }));

    await waitFor(() =>
      expect(lastBody(fetchMock, 'POST', '/admin/orders/PW-2026-000042/mark-paid')).toEqual({
        note: 'Found on the account',
        notifyCustomer: true,
      }),
    );
    expect(
      await within(nextStep).findByRole('button', { name: 'Enter parcel and ship' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Phil', { exact: false })).toBeInTheDocument();
  });

  it('enters a parcel with tracking number and shows the server’s error for an unknown carrier', async () => {
    let attempts = 0;
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/orders/PW-2026-000042': paid,
      'POST /admin/orders/PW-2026-000042/shipments': () =>
        ++attempts === 1 ? problem(422, 'CARRIER_UNKNOWN') : shipped,
    });
    renderRoutes(routes, { path: '/admin/orders/PW-2026-000042' });

    await userEvent.click(await screen.findByRole('button', { name: 'Enter parcel and ship' }));
    const dialog = await screen.findByRole('dialog', { name: 'Enter parcel' });
    const number = within(dialog).getByLabelText('Tracking number');
    await userEvent.type(number, 'x!');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Enter parcel and ship' }));
    expect(within(dialog).getByText('Enter 4 to 40 letters or digits.')).toBeInTheDocument();
    expect(attempts).toBe(0);

    await userEvent.clear(number);
    await userEvent.type(number, 'JJD 0001 2345 6789');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Enter parcel and ship' }));
    expect(await within(dialog).findByText('Please choose one of the listed carriers.')).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Enter parcel and ship' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(lastBody(fetchMock, 'POST', '/admin/orders/PW-2026-000042/shipments')).toEqual({
      carrier: 'DHL',
      trackingNumber: 'JJD 0001 2345 6789',
      notifyCustomer: true,
    });
    const parcels = screen.getByRole('region', { name: 'Parcels' });
    expect(within(parcels).getByRole('link', { name: /Track parcel/ })).toHaveAttribute(
      'href',
      'https://www.dhl.com/tracking?tracking-id=JJD000123456789',
    );
    expect(screen.getByRole('button', { name: 'Mark as delivered' })).toBeInTheDocument();
  });

  it('needs a reason to cancel and reminds the owner to refund a paid order', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/orders/PW-2026-000042': paid,
      'POST /admin/orders/PW-2026-000042/cancel': {
        ...paid,
        order: { ...paid.order, status: 'CANCELLED' },
        actions: noActions,
      },
    });
    renderRoutes(routes, { path: '/admin/orders/PW-2026-000042' });

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const dialog = await screen.findByRole('dialog', { name: 'Cancel order PW-2026-000042?' });
    expect(dialog).toHaveTextContent('Transfer €17.70 back afterwards.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));
    expect(within(dialog).getByText('This field is required.')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText('Reason (internal)'), 'Moulting');
    await userEvent.click(within(dialog).getByLabelText('Put the animals back on sale'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));
    await waitFor(() =>
      expect(lastBody(fetchMock, 'POST', '/admin/orders/PW-2026-000042/cancel')).toEqual({
        reason: 'Moulting',
        restock: false,
        notifyCustomer: true,
      }),
    );
    expect(await screen.findByText('This order is cancelled. Nothing more to do.')).toBeInTheDocument();
  });

  it('prints a packing slip without prices', async () => {
    mockApi({ '/auth/me': owner, '/admin/orders/PW-2026-000042': paid });
    renderRoutes(routes, { path: '/admin/orders/PW-2026-000042/packing-slip' });

    expect(await screen.findByRole('heading', { name: 'Packing slip' })).toBeInTheDocument();
    expect(screen.getByText('Blue Dream')).toBeInTheDocument();
    expect(screen.getByText('Please ring twice')).toBeInTheDocument();
    expect(screen.queryByText('€17.70')).not.toBeInTheDocument();
    const print = vi.fn();
    window.print = print;
    await userEvent.click(screen.getByRole('button', { name: 'Print' }));
    expect(print).toHaveBeenCalled();
  });
});
