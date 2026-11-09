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
    expect(await screen.findByRole('heading', { level: 1, name: /Clear water/ })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main');
    expect(screen.getAllByRole('link', { name: 'PW Aquatics' })[0]).toHaveAttribute('href', '/');
    expect(document.title).toContain('PW Aquatics');
  });

  it.each([
    ['/about', 'Small animals, taken seriously'],
    ['/gallery', 'Impressions from our tanks'],
    ['/contact', 'We would love to hear from you'],
    ['/legal/imprint', 'Legal notice'],
    ['/shop', 'All animals'],
  ])('renders %s', async (path, heading) => {
    renderRoutes(routes, { path });
    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
  });

  it('shows the 404 page for unknown paths', async () => {
    renderRoutes(routes, { path: '/does-not-exist' });
    expect(
      await screen.findByRole('heading', { level: 1, name: 'This page has drifted away' }),
    ).toBeInTheDocument();
  });

  it('shows the 404 page for unknown legal pages', async () => {
    renderRoutes(routes, { path: '/legal/unknown' });
    expect(
      await screen.findByRole('heading', { level: 1, name: 'This page has drifted away' }),
    ).toBeInTheDocument();
  });
});
