import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { RequireAuth } from '@/app/guards/RequireAuth';
import { AccountLayout } from '@/app/layouts/AccountLayout';
import { anna, anonymous } from '@/features/auth/test/fixtures';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { Address, Profile } from '../api/account';
import { AddressesPage } from './AddressesPage';
import { ProfilePage } from './ProfilePage';

const home: Address = {
  id: 'a1',
  label: 'Home',
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
  defaultShipping: true,
  defaultBilling: true,
};
const office: Address = {
  ...home,
  id: 'a2',
  label: 'Office',
  city: 'Hamburg',
  defaultShipping: false,
  defaultBilling: false,
};

const profile: Profile = {
  email: 'anna@example.com',
  firstName: 'Anna',
  lastName: 'Schmidt',
  phone: null,
  marketingOptIn: false,
  locale: 'en',
  memberSince: '2026-11-10T10:00:00Z',
};

// The real guard and layout are part of the test: signing out or deleting the account
// must end on the home page, not bounce through the login page.
const routes = [
  {
    path: '/account',
    element: <RequireAuth />,
    children: [
      {
        element: <AccountLayout />,
        children: [
          { path: 'addresses', element: <AddressesPage /> },
          { path: 'profile', element: <ProfilePage /> },
        ],
      },
    ],
  },
  { path: '/login', element: <h1>Login page</h1> },
  { path: '/', element: <h1>Home page</h1> },
];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('address book', () => {
  it('lists addresses with their defaults and can move the default', async () => {
    const fetchMock = mockApi({
      '/auth/me': anna,
      '/account/addresses': [home, office],
      'PUT /account/addresses/a2/default': [
        { ...office, defaultShipping: true },
        { ...home, defaultShipping: false },
      ],
    });
    renderRoutes(routes, { path: '/account/addresses' });

    const homeCard = (await screen.findByRole('heading', { name: 'Home' })).closest('li')!;
    expect(within(homeCard).getByText('Default delivery')).toBeInTheDocument();
    expect(within(homeCard).getByText('10115 Berlin')).toBeInTheDocument();
    expect(within(homeCard).getByText('Germany')).toBeInTheDocument();

    const officeCard = screen.getByRole('heading', { name: 'Office' }).closest('li')!;
    await userEvent.click(within(officeCard).getByRole('button', { name: 'Use for delivery' }));

    await waitFor(() =>
      expect(
        within(screen.getByRole('heading', { name: 'Office' }).closest('li')!).getByText('Default delivery'),
      ).toBeInTheDocument(),
    );
    expect(lastBody(fetchMock, 'PUT', '/account/addresses/a2/default')).toEqual({
      shipping: true,
      billing: false,
    });
  });

  it('adds an address through the dialog', async () => {
    let saved: Address[] = [];
    const fetchMock = mockApi({
      '/auth/me': anna,
      '/account/addresses': () => saved,
      'POST /account/addresses': () => {
        saved = [home];
        return home;
      },
    });
    renderRoutes(routes, { path: '/account/addresses' });

    expect(await screen.findByText('No addresses yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add address' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add address' });
    // The name is prefilled from the account.
    expect(within(dialog).getByLabelText('First name')).toHaveValue('Anna');

    await userEvent.click(within(dialog).getByRole('button', { name: 'Save address' }));
    expect(await within(dialog).findAllByText('This field is required.')).not.toHaveLength(0);

    await userEvent.type(within(dialog).getByLabelText('Street'), 'Hauptstraße');
    await userEvent.type(within(dialog).getByLabelText('No.'), '12a');
    await userEvent.type(within(dialog).getByLabelText('Postcode'), '10115');
    await userEvent.type(within(dialog).getByLabelText('Town / city'), 'Berlin');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save address' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await screen.findByText('10115 Berlin')).toBeInTheDocument();
    expect(lastBody(fetchMock, 'POST', '/account/addresses')).toEqual({
      firstName: 'Anna',
      lastName: 'Schmidt',
      street: 'Hauptstraße',
      houseNumber: '12a',
      postalCode: '10115',
      city: 'Berlin',
      countryCode: 'DE',
    });
  });

  it('asks before deleting an address', async () => {
    const fetchMock = mockApi({
      '/auth/me': anna,
      '/account/addresses': [home],
      'DELETE /account/addresses/a1': () => undefined,
    });
    renderRoutes(routes, { path: '/account/addresses' });

    await userEvent.click(await screen.findByRole('button', { name: 'Delete address: Home' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete this address?' });
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete address' }));
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true),
    );
  });
});

describe('profile', () => {
  it('saves personal details', async () => {
    const fetchMock = mockApi({
      '/auth/me': anna,
      '/account/profile': profile,
      'PATCH /account/profile': { ...profile, firstName: 'Anne' },
    });
    renderRoutes(routes, { path: '/account/profile' });

    const firstName = await screen.findByLabelText('First name');
    await userEvent.clear(firstName);
    await userEvent.type(firstName, 'Anne');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Your changes have been saved.')).toBeInTheDocument();
    expect(lastBody(fetchMock, 'PATCH', '/account/profile')).toEqual({
      firstName: 'Anne',
      lastName: 'Schmidt',
      marketingOptIn: false,
      locale: 'en',
    });
  });

  it('reports a wrong current password when changing the password', async () => {
    mockApi({
      '/auth/me': anna,
      '/account/profile': profile,
      'PUT /account/password': () => problem(422, 'CURRENT_PASSWORD_INVALID'),
    });
    renderRoutes(routes, { path: '/account/profile' });

    const section = (await screen.findByRole('heading', { name: 'Password' })).closest('section')!;
    await userEvent.type(within(section).getByLabelText('Current password'), 'wrong-password');
    await userEvent.type(within(section).getByLabelText('New password'), 'a-brand-new-password');
    await userEvent.click(within(section).getByRole('button', { name: 'Change password' }));

    expect(await within(section).findByRole('alert')).toHaveTextContent(
      'The current password is not correct.',
    );
  });

  it('deletes the account only after confirming with the password', async () => {
    const fetchMock = mockApi({
      '/auth/me': anna,
      '/account/profile': profile,
      'DELETE /account': () => undefined,
    });
    const { router } = renderRoutes(routes, { path: '/account/profile' });

    await userEvent.click(await screen.findByRole('button', { name: 'Delete my account' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete your account?' });
    const confirm = within(dialog).getByRole('button', { name: 'Delete account permanently' });
    expect(confirm).toBeDisabled();

    await userEvent.type(within(dialog).getByLabelText('Current password'), 'correct-horse-battery');
    await userEvent.click(confirm);

    expect(await screen.findByRole('heading', { name: 'Home page' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(lastBody(fetchMock, 'DELETE', '/account')).toEqual({ currentPassword: 'correct-horse-battery' });
  });
});

describe('sign out', () => {
  it('ends on the home page', async () => {
    let session = anna;
    const fetchMock = mockApi({
      '/auth/me': () => session,
      '/account/addresses': [home],
      'POST /auth/logout': () => {
        session = anonymous;
        return undefined;
      },
    });
    const { router } = renderRoutes(routes, { path: '/account/addresses' });

    await screen.findByRole('heading', { name: 'Home' });
    await userEvent.click(screen.getAllByRole('button', { name: 'Sign out' })[0]!);

    expect(await screen.findByRole('heading', { name: 'Home page' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/auth/logout'))).toBe(true);
  });
});
