import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '@/test/render';
import { ContactForm } from './ContactForm';

function stubFetch(response: () => Response) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    if (String(input).endsWith('/auth/csrf')) {
      document.cookie = 'XSRF-TOKEN=test-token; path=/';
      return new Response(null, { status: 204 });
    }
    return response();
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function fillValidForm() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Name'), 'Anna Schmidt');
  await user.type(screen.getByLabelText('Email address'), 'anna@example.com');
  await user.type(screen.getByLabelText('Subject'), 'Question about care');
  await user.type(screen.getByLabelText('Message'), 'How many shrimp can I keep in a 30 litre tank?');
  return user;
}

afterEach(() => vi.unstubAllGlobals());

describe('ContactForm', () => {
  it('shows validation errors and does not submit an empty form', async () => {
    const fetchMock = stubFetch(() => new Response(null, { status: 202 }));
    renderWithProviders(<ContactForm />);

    await userEvent.click(screen.getByRole('button', { name: 'Send message' }));

    expect(await screen.findByText('Please enter your name.')).toBeInTheDocument();
    expect(screen.getByText('Please enter a valid email address.')).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Please correct the highlighted fields.');
    expect(screen.getByLabelText('Name')).toHaveFocus();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends the message with CSRF header and shows a confirmation', async () => {
    const fetchMock = stubFetch(() => new Response(null, { status: 202 }));
    renderWithProviders(<ContactForm />);

    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Send message' }));

    expect(await screen.findByText('Thank you for your message!')).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit];
    expect(url).toBe('/api/v1/contact');
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'test-token' });
    expect(JSON.parse(init.body as string)).toEqual({
      name: 'Anna Schmidt',
      email: 'anna@example.com',
      subject: 'Question about care',
      message: 'How many shrimp can I keep in a 30 litre tank?',
      website: '',
    });
  });

  it('shows server-side field errors next to the field', async () => {
    stubFetch(
      () =>
        new Response(
          JSON.stringify({
            status: 400,
            code: 'VALIDATION_FAILED',
            errors: [{ field: 'email', code: 'BLOCKED_DOMAIN', message: 'This domain is not allowed.' }],
          }),
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
    );
    renderWithProviders(<ContactForm />);

    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Send message' }));

    expect(await screen.findByText('This domain is not allowed.')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Please check your input.');
  });

  it('explains when the server is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    document.cookie = 'XSRF-TOKEN=x; path=/';
    renderWithProviders(<ContactForm />);

    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Send message' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('No connection to the server.'));
  });
});
