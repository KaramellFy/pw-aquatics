import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { RequireAuth } from '@/app/guards/RequireAuth';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import { anna, anonymous } from '../test/fixtures';
import { LoginPage } from './LoginPage';
import { RegisterPage } from './RegisterPage';
import { ResetPasswordPage } from './ResetPasswordPage';
import { VerifyEmailPage } from './VerifyEmailPage';

const routes = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/verify-email', element: <VerifyEmailPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/account', element: <RequireAuth />, children: [{ path: '*', element: <h1>Account area</h1> }] },
  { path: '/', element: <h1>Home</h1> },
];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('sign in', () => {
  it('signs in and returns to the page the visitor wanted', async () => {
    let session = anonymous;
    const fetchMock = mockApi({
      '/auth/me': () => session,
      'POST /auth/login': () => (session = anna),
    });
    const { router } = renderRoutes(routes, { path: '/account/addresses' });

    // The guard sends anonymous visitors to the login page and remembers where they were going.
    await screen.findByRole('heading', { name: 'Welcome back' });
    expect(router.state.location.search).toBe('?redirect=%2Faccount%2Faddresses');

    await userEvent.type(screen.getByLabelText('Email address'), 'anna@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Account area' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/account/addresses');
    expect(lastBody(fetchMock, 'POST', '/auth/login')).toEqual({
      email: 'anna@example.com',
      password: 'correct-horse-battery',
    });
  });

  it('explains wrong credentials and offers to resend the confirmation email', async () => {
    let failure = problem(401, 'INVALID_CREDENTIALS');
    const fetchMock = mockApi({
      '/auth/me': anonymous,
      'POST /auth/login': () => failure.clone(),
      'POST /auth/resend-verification': () => undefined,
    });
    renderRoutes(routes, { path: '/login' });

    await userEvent.type(await screen.findByLabelText('Email address'), 'anna@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Email address or password is incorrect.');

    failure = problem(403, 'EMAIL_NOT_VERIFIED');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Send the confirmation email again' }));
    expect(await screen.findByText(/a new email is on its way/)).toBeInTheDocument();
    expect(lastBody(fetchMock, 'POST', '/auth/resend-verification')).toEqual({ email: 'anna@example.com' });
  });

  it('never redirects to another site after signing in', async () => {
    mockApi({ '/auth/me': anna });
    const { router } = renderRoutes(routes, { path: '/login?redirect=https%3A%2F%2Fevil.example' });
    await waitFor(() => expect(router.state.location.pathname).toBe('/account'));
  });

  it('can show the password while typing', async () => {
    mockApi({ '/auth/me': anonymous });
    renderRoutes(routes, { path: '/login' });
    const password = await screen.findByLabelText('Password');
    expect(password).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(password).toHaveAttribute('type', 'text');
  });
});

describe('registration', () => {
  it('validates input, then asks the visitor to check their inbox', async () => {
    const fetchMock = mockApi({ '/auth/me': anonymous, 'POST /auth/register': () => undefined });
    renderRoutes(routes, { path: '/register' });

    await userEvent.click(await screen.findByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Please use at least 10 characters.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);

    await userEvent.type(screen.getByLabelText('First name'), 'Anna');
    await userEvent.type(screen.getByLabelText('Last name'), 'Schmidt');
    await userEvent.type(screen.getByLabelText('Email address'), 'anna@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('heading', { name: 'Check your inbox' })).toBeInTheDocument();
    expect(screen.getByText(/anna@example.com/)).toBeInTheDocument();
    expect(lastBody(fetchMock, 'POST', '/auth/register')).toEqual({
      firstName: 'Anna',
      lastName: 'Schmidt',
      email: 'anna@example.com',
      password: 'correct-horse-battery',
      marketingOptIn: false,
    });
  });

  it('shows server-side errors such as a weak password', async () => {
    mockApi({ '/auth/me': anonymous, 'POST /auth/register': () => problem(422, 'PASSWORD_TOO_WEAK') });
    renderRoutes(routes, { path: '/register' });

    await userEvent.type(await screen.findByLabelText('First name'), 'Anna');
    await userEvent.type(screen.getByLabelText('Last name'), 'Schmidt');
    await userEvent.type(screen.getByLabelText('Email address'), 'anna@example.com');
    await userEvent.type(screen.getByLabelText('Password'), '1234567890');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('too easy to guess');
  });
});

describe('emailed links', () => {
  it('confirms an email address exactly once', async () => {
    const fetchMock = mockApi({ '/auth/me': anonymous, 'POST /auth/verify-email': () => undefined });
    renderRoutes(routes, { path: '/verify-email?token=abc123' });

    expect(await screen.findByRole('heading', { name: 'Email address confirmed' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
    const posts = fetchMock.mock.calls.filter(([input]) => String(input).endsWith('/auth/verify-email'));
    expect(posts).toHaveLength(1);
    expect(lastBody(fetchMock, 'POST', '/auth/verify-email')).toEqual({ token: 'abc123' });
  });

  it('explains a link that no longer works', async () => {
    mockApi({ '/auth/me': anonymous, 'POST /auth/verify-email': () => problem(422, 'TOKEN_INVALID') });
    renderRoutes(routes, { path: '/verify-email?token=used' });
    expect(await screen.findByRole('heading', { name: 'This link no longer works' })).toBeInTheDocument();
  });

  it('sets a new password from a reset link', async () => {
    const fetchMock = mockApi({ '/auth/me': anonymous, 'POST /auth/reset-password': () => undefined });
    renderRoutes(routes, { path: '/reset-password?token=reset-1' });

    await userEvent.type(await screen.findByLabelText('New password'), 'a-brand-new-password');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));

    expect(await screen.findByRole('heading', { name: 'Password changed' })).toBeInTheDocument();
    expect(lastBody(fetchMock, 'POST', '/auth/reset-password')).toEqual({
      token: 'reset-1',
      newPassword: 'a-brand-new-password',
    });
  });

  it('asks for a new link when the reset link is missing or expired', async () => {
    mockApi({ '/auth/me': anonymous });
    renderRoutes(routes, { path: '/reset-password' });
    expect(await screen.findByRole('link', { name: 'Request a new link' })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
  });
});
