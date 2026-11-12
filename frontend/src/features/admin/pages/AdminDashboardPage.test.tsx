import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { RequireAdmin } from '@/app/guards/RequireAdmin';
import { AdminLayout } from '@/app/layouts/AdminLayout';
import { anna } from '@/features/auth/test/fixtures';
import type { Session } from '@/features/auth/api/auth';
import { mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { Dashboard, ProductStats, SalesStats, StockStats } from '../api/statistics';
import { AdminDashboardPage } from './AdminDashboardPage';

const owner: Session = { authenticated: true, user: { ...anna.user!, role: 'ADMIN' } };
const eur = (amount: number) => ({ amount, currency: 'EUR' });

const dashboard: Dashboard = {
  awaitingPaymentOrders: 2,
  awaitingPayment: eur(6980),
  ordersToShip: 3,
  liveProducts: 12,
  lowStockProducts: 1,
  soldOutProducts: 1,
  recentOrders: [
    {
      orderNumber: 'PW-2026-000042',
      placedAt: '2026-11-12T09:15:00Z',
      status: 'PENDING_PAYMENT',
      paymentStatus: 'UNPAID',
      total: eur(3490),
      customerName: 'Ben Stats',
      countryCode: 'GB',
      animals: 10,
    },
  ],
  lowStock: [
    { id: 'p1', name: 'Blue Dream', stockUnits: 0, lowStockThreshold: 5 },
    { id: 'p2', name: 'Red Fire', stockUnits: 3, lowStockThreshold: 5 },
  ],
};

const bucket = (start: string, paid: number, awaiting: number) => ({
  start,
  revenue: eur(paid + awaiting),
  orders: (paid > 0 ? 1 : 0) + (awaiting > 0 ? 1 : 0),
  animals: 10,
  paidRevenue: eur(paid),
  awaitingRevenue: eur(awaiting),
  paidOrders: paid > 0 ? 1 : 0,
  awaitingOrders: awaiting > 0 ? 1 : 0,
  paidAnimals: 5,
  awaitingAnimals: 5,
});

const sales = (from: string, to: string): SalesStats => ({
  period: { from, to, granularity: 'DAY', previousFrom: '2026-09-14', previousTo: '2026-10-13' },
  kpis: {
    revenue: { value: eur(14480), previous: eur(2000), changeBps: 62400 },
    paidRevenue: { value: eur(10990), previous: eur(2000), changeBps: 44950 },
    orders: { value: 3, previous: 1, changeBps: 20000 },
    averageOrderValue: { value: eur(4827), previous: eur(2000), changeBps: 14135 },
    animalsSold: { value: 40, previous: 5, changeBps: 70000 },
    newAccounts: { value: 2, previous: 1, changeBps: 10000 },
    returningCustomerShare: { valueBps: 3333, previousBps: 0 },
    cancelledOrders: { value: 1, previous: 0, changeBps: null },
    cancellationRate: { valueBps: 2500, previousBps: 0 },
  },
  series: [bucket('2026-11-10', 3990, 3490), bucket('2026-11-11', 7000, 0), bucket('2026-11-12', 0, 0)],
  byStatus: [
    { status: 'PENDING_PAYMENT', orders: 1, total: eur(3490) },
    { status: 'CANCELLED', orders: 1, total: eur(1290) },
  ],
  byWeekday: [1, 2, 3, 4, 5, 6, 7].map((weekday) => ({
    weekday,
    orders: weekday === 1 ? 2 : 0,
    revenue: eur(weekday === 1 ? 7480 : 0),
  })),
  byCountry: [{ countryCode: 'DE', orders: 2, revenue: eur(10990) }],
});

const productStats: ProductStats = {
  period: sales('2026-10-14', '2026-11-12').period,
  topProducts: [
    { productId: 'p1', name: 'Stats Red', gradeName: 'Premium', units: 30, packs: 30, revenue: eur(9000) },
  ],
  byCategory: [{ name: 'Neocaridina', units: 30, revenue: eur(9000) }],
  bySpecies: [{ name: null, units: 2, revenue: eur(1000) }],
};

const stockStats: StockStats = {
  period: productStats.period,
  restocked: { value: 50, previous: 20, changeBps: 15000 },
  sold: { value: 40, previous: 5, changeBps: 70000 },
  lost: { value: 5, previous: 1, changeBps: 40000 },
  lossRate: { valueBps: 1111, previousBps: 1667 },
  byReason: [
    { reason: 'RESTOCK', units: 50 },
    { reason: 'LOSS', units: -5 },
  ],
};

const routes = [
  {
    path: '/admin',
    element: <RequireAdmin />,
    children: [{ element: <AdminLayout />, children: [{ index: true, element: <AdminDashboardPage /> }] }],
  },
];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

function mockStats() {
  return mockApi({
    '/auth/me': owner,
    '/admin/dashboard': dashboard,
    '/admin/stats/sales': (url: URL) =>
      sales(url.searchParams.get('from') ?? '', url.searchParams.get('to') ?? ''),
    '/admin/stats/products': productStats,
    '/admin/stats/stock': stockStats,
  });
}

describe('admin dashboard', () => {
  it('shows what needs attention and the key figures of the period', async () => {
    mockStats();
    renderRoutes(routes, { path: '/admin' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
    expect(await screen.findByText('€69.80')).toBeInTheDocument();
    expect(screen.getByText('2 orders waiting for a bank transfer')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Show low stock' })).toHaveAttribute(
      'href',
      '/admin/inventory?stock=LOW_STOCK',
    );

    const kpis = await screen.findByRole('list', { name: 'Key figures' });
    const revenue = within(kpis).getByText('Revenue').closest('div')!;
    expect(within(revenue).getByText('€144.80')).toBeInTheDocument();
    expect(within(revenue).getByText('+624%')).toBeInTheDocument();
    expect(within(revenue).getByText('(better than before)')).toBeInTheDocument();
    expect(within(revenue).getByText('before: €20.00')).toBeInTheDocument();
    expect(within(kpis).getByText('33.3%')).toBeInTheDocument();
    expect(within(kpis).getByText('1 order cancelled or expired')).toBeInTheDocument();
    const stockKpis = screen.getByRole('list', { name: 'Stock figures' });
    expect(within(stockKpis).getByText('11.1%')).toBeInTheDocument();
    expect(within(stockKpis).getByText('before: 16.7%')).toBeInTheDocument();
    // More losses than before is shown as worse, in words as well as colour.
    const lost = within(stockKpis).getByText('Animals lost').closest('div')!;
    expect(within(lost).getByText('(worse than before)')).toBeInTheDocument();

    // Breakdowns print their values; null species gets a readable name.
    expect(screen.getByText('Stats Red · Premium')).toBeInTheDocument();
    expect(screen.getByText('No species (e.g. food, accessories)')).toBeInTheDocument();
    expect(screen.getByText('Germany')).toBeInTheDocument();
    expect(screen.getByText('+50')).toBeInTheDocument();
    expect(screen.getByText('−5')).toBeInTheDocument();
    expect(screen.getByText('PW-2026-000042')).toBeInTheDocument();
    expect(screen.getByText('3 left')).toBeInTheDocument();
  });

  it('asks the server for the chosen period', async () => {
    const fetchMock = mockStats();
    renderRoutes(routes, { path: '/admin?period=7d' });
    await screen.findByRole('list', { name: 'Key figures' });

    const salesCalls = () =>
      fetchMock.mock.calls.map(([input]) => String(input)).filter((url) => url.includes('/stats/sales?'));
    expect(salesCalls().at(-1)).toMatch(/from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}/);
    expect(screen.getByRole('button', { name: 'Last 7 days' })).toHaveAttribute('aria-pressed', 'true');

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Custom' }));
    await user.clear(screen.getByLabelText('From'));
    await user.type(screen.getByLabelText('From'), '2026-02-01');
    await user.clear(screen.getByLabelText('To'));
    await user.type(screen.getByLabelText('To'), '2026-01-01');
    await user.click(screen.getByRole('button', { name: 'Show' }));
    expect(screen.getByRole('alert')).toHaveTextContent('The end date must not be before the start date.');

    await user.clear(screen.getByLabelText('To'));
    await user.type(screen.getByLabelText('To'), '2026-02-28');
    await user.click(screen.getByRole('button', { name: 'Show' }));
    await waitFor(() => expect(salesCalls().at(-1)).toContain('from=2026-02-01&to=2026-02-28'));
    expect(screen.getByRole('link', { name: 'Download CSV' })).toHaveAttribute(
      'href',
      '/api/v1/admin/stats/sales/export?from=2026-02-01&to=2026-02-28',
    );
  });

  it('reads chart values by keyboard and as a table', async () => {
    mockStats();
    renderRoutes(routes, { path: '/admin' });
    const chart = await screen.findByRole('group', { name: 'Revenue over time' });

    const user = userEvent.setup();
    chart.focus();
    // Focus starts on the latest day; the arrow key moves to the day before.
    await user.keyboard('{ArrowLeft}');
    const tooltip = await screen.findByRole('status');
    expect(tooltip).toHaveTextContent('Wednesday, November 11, 2026');
    expect(tooltip).toHaveTextContent('€70.00');

    await user.click(screen.getByRole('button', { name: 'Orders' }));
    expect(screen.getByRole('group', { name: 'Orders over time' })).toBeInTheDocument();

    const card = screen.getByRole('region', { name: 'Sales over time' });
    await user.click(within(card).getByRole('button', { name: 'Show as table' }));
    const table = within(card).getByRole('table', { name: 'Sales over time' });
    expect(within(table).getByRole('rowheader', { name: 'Tuesday, November 10, 2026' })).toBeInTheDocument();
    expect(within(table).getAllByRole('row')).toHaveLength(4);
  });

  it('explains an invalid period from the server', async () => {
    mockApi({
      '/auth/me': owner,
      '/admin/dashboard': dashboard,
      '/admin/stats/sales': problem(422, 'STATS_RANGE_INVALID'),
      '/admin/stats/products': problem(422, 'STATS_RANGE_INVALID'),
      '/admin/stats/stock': problem(422, 'STATS_RANGE_INVALID'),
    });
    renderRoutes(routes, { path: '/admin?from=2020-01-01&to=2026-01-01' });
    expect(
      await screen.findByText('Please choose a period of at most two years, with the end after the start.'),
    ).toBeInTheDocument();
  });
});
