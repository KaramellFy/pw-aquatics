import { screen } from '@testing-library/react';
import { beforeAll, describe, expect, it } from 'vitest';
import { renderRoutes } from '@/test/render';
import { routes } from './routes';

beforeAll(() => {
  // jsdom lacks these browser APIs used by the layout
  window.scrollTo = () => {};
});

describe('routes', () => {
  it('renders the home page inside the public layout', async () => {
    renderRoutes(routes, { path: '/' });
    expect(await screen.findByRole('heading', { level: 1, name: /Kleine Tiere/ })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Zum Inhalt springen' })).toHaveAttribute('href', '#main');
  });

  it.each([
    ['/about', 'Aus Leidenschaft für das Kleine'],
    ['/gallery', 'Eindrücke aus unseren Becken'],
    ['/contact', 'Wir freuen uns auf deine Nachricht'],
    ['/legal/impressum', 'Impressum'],
    ['/shop', 'Der Shop entsteht gerade'],
  ])('renders %s', async (path, heading) => {
    renderRoutes(routes, { path });
    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
  });

  it('shows the 404 page for unknown paths', async () => {
    renderRoutes(routes, { path: '/does-not-exist' });
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Diese Seite ist abgetaucht' }),
    ).toBeInTheDocument();
  });

  it('shows the 404 page for unknown legal pages', async () => {
    renderRoutes(routes, { path: '/legal/unknown' });
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Diese Seite ist abgetaucht' }),
    ).toBeInTheDocument();
  });
});
