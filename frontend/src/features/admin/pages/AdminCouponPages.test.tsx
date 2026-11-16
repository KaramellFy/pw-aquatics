import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { RequireAdmin } from '@/app/guards/RequireAdmin';
import { AdminLayout } from '@/app/layouts/AdminLayout';
import { anna } from '@/features/auth/test/fixtures';
import type { Session } from '@/features/auth/api/auth';
import { lastBody, mockApi } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { AdminCoupon } from '../api/coupons';
import { AdminCouponDetailPage } from './AdminCouponDetailPage';
import { AdminCouponsPage } from './AdminCouponsPage';

const owner: Session = { authenticated: true, user: { ...anna.user!, role: 'ADMIN' } };
const money = (amount: number) => ({ amount, currency: 'EUR' });

const spring: AdminCoupon = {
  id: 'c1',
  code: 'SPRING10',
  description: 'Spring sale',
  descriptionDe: null,
  type: 'PERCENTAGE',
  percentage: 1000,
  amount: null,
  maxDiscount: money(2000),
  minOrder: money(3000),
  startsOn: '2026-11-01',
  endsOn: '2026-11-30',
  usageLimitTotal: 100,
  usageLimitPerCustomer: 1,
  timesRedeemed: 1,
  status: 'ACTIVE',
  active: true,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
  version: 3,
};
const shipFree: AdminCoupon = {
  ...spring,
  id: 'c2',
  code: 'SHIPFREE',
  description: null,
  type: 'FREE_SHIPPING',
  percentage: null,
  maxDiscount: null,
  minOrder: null,
  startsOn: null,
  endsOn: null,
  usageLimitTotal: null,
  usageLimitPerCustomer: null,
  timesRedeemed: 0,
  status: 'INACTIVE',
  active: false,
  version: 0,
};

const routes = [
  {
    path: '/admin',
    element: <RequireAdmin />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          { path: 'coupons', element: <AdminCouponsPage /> },
          { path: 'coupons/:id', element: <AdminCouponDetailPage /> },
        ],
      },
    ],
  },
];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('coupon list', () => {
  it('shows what each coupon gives and filters by the status the server decided', async () => {
    mockApi({ '/auth/me': owner, '/admin/coupons': [spring, shipFree] });
    renderRoutes(routes, { path: '/admin/coupons' });

    const row = (await screen.findByRole('link', { name: 'SPRING10' })).closest('tr')!;
    expect(within(row).getByText('10% off')).toBeInTheDocument();
    expect(within(row).getByText('from €30.00')).toBeInTheDocument();
    expect(within(row).getByText('1 of 100')).toBeInTheDocument();
    expect(within(row).getByText('Active')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'SHIPFREE' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Switched off/ }));
    expect(screen.queryByRole('link', { name: 'SPRING10' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'SHIPFREE' })).toBeInTheDocument();
  });

  it('creates a coupon from typed values: percent and euros become hundredths', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/coupons': [],
      'POST /admin/coupons': { ...spring, id: 'c9', code: 'AUTUMN', percentage: 1250 },
      '/admin/coupons/c9': { ...spring, id: 'c9', code: 'AUTUMN', percentage: 1250 },
      '/admin/coupons/c9/usage': { orders: 0, saving: money(0), revenue: money(0) },
      '/admin/coupons/c9/redemptions': { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 },
    });
    renderRoutes(routes, { path: '/admin/coupons' });

    expect(await screen.findByText(/No coupons yet/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New coupon' }));
    const dialog = await screen.findByRole('dialog', { name: 'New coupon' });
    await userEvent.type(within(dialog).getByLabelText('Code'), 'au');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(within(dialog).getByText(/Use 3–32 letters/)).toBeInTheDocument();
    expect(within(dialog).getByText('Enter a valid number.')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText('Code'), 'tumn');
    await userEvent.type(within(dialog).getByLabelText('Discount (%)'), '12,5');
    await userEvent.type(within(dialog).getByLabelText(/Minimum order value/), '30');
    await userEvent.type(within(dialog).getByLabelText(/Uses per customer/), '1');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(lastBody(fetchMock, 'POST', '/admin/coupons')).toEqual({
        code: 'AUTUMN',
        type: 'PERCENTAGE',
        value: 1250,
        minOrderCents: 3000,
        usageLimitPerCustomer: 1,
        active: true,
        version: 0,
      }),
    );
    expect(await screen.findByRole('heading', { name: 'AUTUMN' })).toBeInTheDocument();
  });
});

describe('coupon page', () => {
  it('shows what the coupon brought, its orders, and switches it off with the loaded version', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/coupons/c1': spring,
      '/admin/coupons/c1/usage': { orders: 1, saving: money(300), revenue: money(2690) },
      '/admin/coupons/c1/redemptions': {
        items: [
          {
            orderNumber: 'PW-2026-000042',
            placedAt: '2026-11-10T10:00:00Z',
            customerName: 'Anna Schmidt',
            orderStatus: 'PAID',
            orderTotal: money(2690),
            saving: money(300),
            released: false,
          },
        ],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      },
      'PUT /admin/coupons/c1': { ...spring, active: false, status: 'INACTIVE', version: 4 },
    });
    renderRoutes(routes, { path: '/admin/coupons/c1' });

    expect(await screen.findByRole('heading', { name: 'SPRING10' })).toBeInTheDocument();
    expect(await screen.findByText('€26.90')).toBeInTheDocument();
    const orders = screen.getByRole('region', { name: 'Orders with this code' });
    expect(within(orders).getByRole('link', { name: 'PW-2026-000042' })).toHaveAttribute(
      'href',
      '/admin/orders/PW-2026-000042',
    );
    expect(within(orders).getByText(/Anna Schmidt/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Switch off' }));
    await waitFor(() =>
      expect(lastBody(fetchMock, 'PUT', '/admin/coupons/c1')).toMatchObject({
        type: 'PERCENTAGE',
        value: 1000,
        minOrderCents: 3000,
        maxDiscountCents: 2000,
        active: false,
        version: 3,
      }),
    );
    expect(await screen.findByRole('button', { name: 'Switch on' })).toBeInTheDocument();
  });
});
