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
  await user.type(screen.getByLabelText('E-Mail-Adresse'), 'anna@example.com');
  await user.type(screen.getByLabelText('Betreff'), 'Frage zur Haltung');
  await user.type(screen.getByLabelText('Nachricht'), 'Wie viele Garnelen passen in ein 30-Liter-Becken?');
  return user;
}

afterEach(() => vi.unstubAllGlobals());

describe('ContactForm', () => {
  it('shows validation errors and does not submit an empty form', async () => {
    const fetchMock = stubFetch(() => new Response(null, { status: 202 }));
    renderWithProviders(<ContactForm />);

    await userEvent.click(screen.getByRole('button', { name: 'Nachricht senden' }));

    expect(await screen.findByText('Bitte gib deinen Namen an.')).toBeInTheDocument();
    expect(screen.getByText('Bitte gib eine gültige E-Mail-Adresse an.')).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends the message with CSRF header and shows a confirmation', async () => {
    const fetchMock = stubFetch(() => new Response(null, { status: 202 }));
    renderWithProviders(<ContactForm />);

    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Nachricht senden' }));

    expect(await screen.findByText('Danke für deine Nachricht!')).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit];
    expect(url).toBe('/api/v1/contact');
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'test-token' });
    expect(JSON.parse(init.body as string)).toEqual({
      name: 'Anna Schmidt',
      email: 'anna@example.com',
      subject: 'Frage zur Haltung',
      message: 'Wie viele Garnelen passen in ein 30-Liter-Becken?',
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
            errors: [{ field: 'email', code: 'BLOCKED_DOMAIN', message: 'Diese Domain ist nicht erlaubt.' }],
          }),
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
    );
    renderWithProviders(<ContactForm />);

    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Nachricht senden' }));

    expect(await screen.findByText('Diese Domain ist nicht erlaubt.')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Bitte überprüfe deine Eingaben.');
  });

  it('explains when the server is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    document.cookie = 'XSRF-TOKEN=x; path=/';
    renderWithProviders(<ContactForm />);

    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Nachricht senden' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Keine Verbindung zum Server.'));
  });
});
