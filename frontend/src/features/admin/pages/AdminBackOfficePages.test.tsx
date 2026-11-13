import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { RequireAdmin } from '@/app/guards/RequireAdmin';
import { AdminLayout } from '@/app/layouts/AdminLayout';
import { anna } from '@/features/auth/test/fixtures';
import type { Session } from '@/features/auth/api/auth';
import { lastBody, mockApi } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { CustomerDetail } from '../api/customers';
import type { GalleryImage } from '../api/gallery';
import type { ContactMessage } from '../api/inbox';
import type { AdminShippingMethod, ShippingPause } from '../api/shipping';
import { AdminCustomerDetailPage } from './AdminCustomerDetailPage';
import { AdminGalleryPage } from './AdminGalleryPage';
import { AdminInboxPage } from './AdminInboxPage';
import { AdminShippingPage } from './AdminShippingPage';

const owner: Session = { authenticated: true, user: { ...anna.user!, role: 'ADMIN' } };
const money = (amount: number) => ({ amount, currency: 'EUR' });

const routes = [
  {
    path: '/admin',
    element: <RequireAdmin />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          { path: 'customers/:id', element: <AdminCustomerDetailPage /> },
          { path: 'inbox', element: <AdminInboxPage /> },
          { path: 'gallery', element: <AdminGalleryPage /> },
          { path: 'shipping', element: <AdminShippingPage /> },
        ],
      },
    ],
  },
];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('customer page', () => {
  const customer: CustomerDetail = {
    id: 'u1',
    firstName: 'Lena',
    lastName: 'Fischer',
    email: 'lena@example.com',
    phone: null,
    locale: 'de',
    status: 'ACTIVE',
    lockable: true,
    emailVerified: true,
    marketingOptIn: false,
    registeredAt: '2026-11-01T10:00:00Z',
    lastLoginAt: '2026-11-12T10:00:00Z',
    orderCount: 2,
    paidOrderCount: 1,
    lifetimeValue: money(1770),
    averageOrderValue: money(1770),
    addresses: [],
    orders: [
      {
        orderNumber: 'PW-2026-000042',
        placedAt: '2026-11-10T10:00:00Z',
        status: 'PAID',
        paymentStatus: 'PAID',
        total: money(1770),
        itemCount: 2,
      },
    ],
  };

  it('shows what the customer has spent and locks the account with a reason', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/customers/u1': customer,
      'POST /admin/customers/u1/lock': { ...customer, status: 'LOCKED' },
    });
    renderRoutes(routes, { path: '/admin/customers/u1' });

    expect(await screen.findByRole('heading', { name: 'Lena Fischer' })).toBeInTheDocument();
    expect(screen.getAllByText('€17.70').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'PW-2026-000042' })).toHaveAttribute(
      'href',
      '/admin/orders/PW-2026-000042',
    );

    await userEvent.click(screen.getByRole('button', { name: 'Lock account' }));
    const dialog = await screen.findByRole('dialog', { name: 'Lock the account of Lena Fischer?' });
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Chargebacks');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lock account' }));

    await waitFor(() =>
      expect(lastBody(fetchMock, 'POST', '/admin/customers/u1/lock')).toEqual({ reason: 'Chargebacks' }),
    );
    expect(
      await screen.findByText('This account is locked. The customer cannot sign in.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Unlock account' })).toBeInTheDocument();
  });
});

describe('inbox', () => {
  const message: ContactMessage = {
    id: 'm1',
    name: 'Lena',
    email: 'lena@example.com',
    subject: 'Amano shrimp',
    message: 'Do you ship to Austria?',
    orderNumber: null,
    status: 'NEW',
    createdAt: '2026-11-12T10:00:00Z',
  };

  it('lists new messages, offers a reply by email and marks them as answered', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/contact-messages': { items: [message], page: 0, size: 20, totalItems: 1, totalPages: 1 },
      '/admin/contact-messages/counts': { NEW: 1, READ: 0, ANSWERED: 0, SPAM: 0 },
      'PATCH /admin/contact-messages/m1': { ...message, status: 'ANSWERED' },
    });
    renderRoutes(routes, { path: '/admin/inbox' });

    expect(await screen.findByRole('heading', { name: 'Amano shrimp' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /New/ })).toHaveTextContent('1');
    expect(screen.getByRole('link', { name: 'Reply by email' })).toHaveAttribute(
      'href',
      'mailto:lena@example.com?subject=Re%3A%20Amano%20shrimp',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Mark as answered' }));
    await waitFor(() =>
      expect(lastBody(fetchMock, 'PATCH', '/admin/contact-messages/m1')).toEqual({ status: 'ANSWERED' }),
    );
  });
});

describe('gallery manager', () => {
  const photo = (id: string, published: boolean, version = 0): GalleryImage => ({
    id,
    title: `Photo ${id}`,
    caption: null,
    altText: `Shrimp ${id}`,
    published,
    sortOrder: 0,
    image: {
      url: `/media/gallery/${id}/800.webp`,
      srcSet: '',
      alt: `Shrimp ${id}`,
      width: 1600,
      height: 1200,
    },
    createdAt: '2026-11-12T10:00:00Z',
    version,
  });

  it('needs a description before uploading, publishes and reorders photos', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/gallery': [photo('a', false, 3), photo('b', true)],
      'PUT /admin/gallery/a': photo('a', true, 4),
      'PATCH /admin/gallery/order': [photo('b', true), photo('a', false, 3)],
    });
    renderRoutes(routes, { path: '/admin/gallery' });

    expect(await screen.findByText('Photo a')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Upload photo' }));
    expect(screen.getByText('Choose a photo.')).toBeInTheDocument();
    expect(screen.getByText('This field is required.')).toBeInTheDocument();

    await userEvent.click(screen.getAllByRole('button', { name: 'Show' })[0]!);
    await waitFor(() =>
      expect(lastBody(fetchMock, 'PUT', '/admin/gallery/a')).toEqual({
        title: 'Photo a',
        altText: 'Shrimp a',
        published: true,
        version: 3,
      }),
    );

    await userEvent.click(screen.getByRole('button', { name: 'Move photo 1 later' }));
    await waitFor(() =>
      expect(lastBody(fetchMock, 'PATCH', '/admin/gallery/order')).toEqual({ imageIds: ['b', 'a'] }),
    );
  });
});

describe('shipping settings', () => {
  const method: AdminShippingMethod = {
    id: 's1',
    code: 'DHL_EXPRESS_LIVE',
    name: 'DHL Express',
    description: null,
    carrier: 'DHL',
    price: money(990),
    freeFrom: money(10000),
    liveAnimalsAllowed: true,
    dispatchDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY'],
    estimatedDaysMin: 1,
    estimatedDaysMax: 2,
    countries: ['AT', 'DE'],
    active: true,
    sortOrder: 0,
    version: 2,
  };
  const noPause: ShippingPause = { active: false, inForce: false, message: null, until: null };

  it('switches on the shipping pause with a last day and a notice', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/shipping-methods': [method],
      '/admin/settings/shipping-pause': noPause,
      'PUT /admin/settings/shipping-pause': {
        active: true,
        inForce: true,
        message: 'Heat wave',
        until: '2026-11-20',
      },
    });
    renderRoutes(routes, { path: '/admin/shipping' });

    const card = await screen.findByRole('region', { name: 'Shipping pause for live animals' });
    await userEvent.click(within(card).getByLabelText('Pause shipping of live animals'));
    await userEvent.type(within(card).getByLabelText(/Notice for customers/), 'Heat wave');
    await userEvent.type(within(card).getByLabelText('Last day of the pause'), '2026-11-20');
    await userEvent.click(within(card).getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(lastBody(fetchMock, 'PUT', '/admin/settings/shipping-pause')).toEqual({
        active: true,
        message: 'Heat wave',
        until: '2026-11-20',
      }),
    );
    expect(
      await screen.findByText(/The pause is on: no live animals leave up to and including/),
    ).toBeInTheDocument();
  });

  it('edits a method: prices typed as 12,90, the code stays, the version is sent', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/shipping-methods': [method],
      '/admin/settings/shipping-pause': noPause,
      'PUT /admin/shipping-methods/s1': { ...method, price: money(1290), version: 3 },
    });
    renderRoutes(routes, { path: '/admin/shipping' });

    const row = (await screen.findByText('DHL Express')).closest('tr')!;
    expect(within(row).getByText('Mon, Tue, Wed')).toBeInTheDocument();
    await userEvent.click(within(row).getByRole('button', { name: 'Edit DHL Express' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit DHL Express' });
    const price = within(dialog).getByLabelText('Price (€)');
    await userEvent.clear(price);
    await userEvent.type(price, '12,90');
    await userEvent.click(within(dialog).getByLabelText('Thursday'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(lastBody(fetchMock, 'PUT', '/admin/shipping-methods/s1')).toMatchObject({
        priceCents: 1290,
        freeShippingThresholdCents: 10000,
        dispatchDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY'],
        countries: ['AT', 'DE'],
        version: 2,
      }),
    );
    expect(lastBody(fetchMock, 'PUT', '/admin/shipping-methods/s1')).not.toHaveProperty('code');
  });
});
