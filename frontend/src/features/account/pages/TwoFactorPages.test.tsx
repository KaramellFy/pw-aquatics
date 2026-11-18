import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { RequireAdmin } from '@/app/guards/RequireAdmin';
import { RequireAuth } from '@/app/guards/RequireAuth';
import { owner, ownerWithoutTwoFactor } from '@/features/auth/test/fixtures';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { Profile } from '../api/account';
import type { TwoFactorStatus } from '../api/twoFactor';
import { ProfilePage } from './ProfilePage';
import { TwoFactorSetupPage } from './TwoFactorSetupPage';

const routes = [
  {
    path: '/account',
    element: <RequireAuth />,
    children: [
      { path: 'two-factor', element: <TwoFactorSetupPage /> },
      { path: 'profile', element: <ProfilePage /> },
    ],
  },
  { path: '/admin', element: <RequireAdmin />, children: [{ path: '*', element: <h1>Admin area</h1> }] },
];

const off: TwoFactorStatus = { enabled: false, enabledAt: null, recoveryCodesLeft: 0 };
const on: TwoFactorStatus = { enabled: true, enabledAt: '2026-11-18T09:00:00Z', recoveryCodesLeft: 9 };
const codes = [
  'abcd-efgh-2222',
  'abcd-efgh-2223',
  'abcd-efgh-2224',
  'abcd-efgh-2225',
  'abcd-efgh-2226',
  'abcd-efgh-2227',
  'abcd-efgh-2232',
  'abcd-efgh-2233',
  'abcd-efgh-2234',
  'abcd-efgh-2235',
];
const setup = {
  secret: 'JBSWY3DPEHPK3PXP',
  otpauthUri: 'otpauth://totp/PW%20Aquatics:owner%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=PW%20Aquatics',
};
const profile: Profile = {
  email: 'owner@example.com',
  firstName: 'Anna',
  lastName: 'Schmidt',
  phone: null,
  marketingOptIn: false,
  locale: 'en',
  memberSince: '2026-11-10T10:00:00Z',
  hasPassword: true,
  googleEmail: null,
  confirmedUntil: null,
};

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('two-step sign-in for administrators', () => {
  it('sends administrators without it from the admin area to its setup', async () => {
    mockApi({ '/auth/me': ownerWithoutTwoFactor, '/account/two-factor': off });
    const { router } = renderRoutes(routes, { path: '/admin/orders?status=PAID' });

    expect(await screen.findByRole('heading', { name: 'Set up two-step sign-in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/account/two-factor');
    expect(router.state.location.search).toBe('?redirect=%2Fadmin%2Forders%3Fstatus%3DPAID');
  });

  it('sets up the app with a QR code, its first code and recovery codes, then goes back', async () => {
    let session = ownerWithoutTwoFactor;
    const fetchMock = mockApi({
      '/auth/me': () => session,
      '/account/two-factor': off,
      'POST /account/two-factor/setup': setup,
      'POST /account/two-factor/confirm': () => {
        session = owner;
        return { recoveryCodes: codes };
      },
    });
    const { router } = renderRoutes(routes, { path: '/account/two-factor?redirect=%2Fadmin%2Forders' });

    await userEvent.click(await screen.findByRole('button', { name: 'Start setup' }));
    expect(
      await screen.findByRole('img', { name: 'QR code for your authenticator app' }),
    ).toBeInTheDocument();
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP')).toBeInTheDocument();
    expect(lastBody(fetchMock, 'POST', '/account/two-factor/setup')).toEqual({});

    await userEvent.type(screen.getByLabelText('Code from the app'), '654321');
    await userEvent.click(screen.getByRole('button', { name: 'Turn on' }));

    const list = await screen.findByRole('list', { name: 'Recovery codes' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(10);
    expect(screen.getByRole('button', { name: 'Download as text file' })).toBeInTheDocument();
    expect(lastBody(fetchMock, 'POST', '/account/two-factor/confirm')).toEqual({ code: '654321' });

    await userEvent.click(screen.getByRole('link', { name: 'Continue' }));
    expect(await screen.findByRole('heading', { name: 'Admin area' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/orders');
  });

  it('shows why a code was refused', async () => {
    mockApi({
      '/auth/me': ownerWithoutTwoFactor,
      '/account/two-factor': off,
      'POST /account/two-factor/setup': setup,
      'POST /account/two-factor/confirm': () => problem(422, 'TWO_FACTOR_CODE_INVALID'),
    });
    renderRoutes(routes, { path: '/account/two-factor' });

    await userEvent.click(await screen.findByRole('button', { name: 'Start setup' }));
    await userEvent.type(await screen.findByLabelText('Code from the app'), '000000');
    await userEvent.click(screen.getByRole('button', { name: 'Turn on' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('This code is not correct.');
    expect(screen.getByRole('img', { name: 'QR code for your authenticator app' })).toBeInTheDocument();
  });

  it('asks for a current code before moving to a new phone', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/account/two-factor': on,
      'POST /account/two-factor/setup': setup,
    });
    renderRoutes(routes, { path: '/account/two-factor' });

    await userEvent.type(await screen.findByLabelText('Code from your current app'), 'abcd-efgh-2345');
    await userEvent.click(screen.getByRole('button', { name: 'Start setup' }));

    expect(
      await screen.findByRole('img', { name: 'QR code for your authenticator app' }),
    ).toBeInTheDocument();
    expect(lastBody(fetchMock, 'POST', '/account/two-factor/setup')).toEqual({ code: 'abcdefgh2345' });
  });

  it('shows the status on the profile and renews the recovery codes', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/auth/sign-in-options': { google: false },
      '/account/profile': profile,
      '/account/two-factor': on,
      'POST /account/two-factor/recovery-codes': { recoveryCodes: codes },
    });
    renderRoutes(routes, { path: '/account/profile' });

    expect(await screen.findByRole('heading', { name: 'Two-step sign-in' })).toBeInTheDocument();
    expect(await screen.findByText(/On since November 18, 2026/)).toHaveTextContent('9 recovery codes left.');
    expect(screen.getByRole('link', { name: 'Move to a new phone' })).toHaveAttribute(
      'href',
      '/account/two-factor',
    );

    await userEvent.click(screen.getByRole('button', { name: 'New recovery codes' }));
    await userEvent.type(screen.getByLabelText('Code from your current app'), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Create new codes' }));

    expect(await screen.findByRole('list', { name: 'Recovery codes' })).toBeInTheDocument();
    expect(lastBody(fetchMock, 'POST', '/account/two-factor/recovery-codes')).toEqual({ code: '123456' });
  });
});
