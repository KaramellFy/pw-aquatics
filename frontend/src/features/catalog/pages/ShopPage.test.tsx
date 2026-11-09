import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { mockApi } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import { blackRose, blueDream, facets, page, taxonomy } from '../test/fixtures';
import { ShopPage } from './ShopPage';

const routes = [
  { path: '/shop', element: <ShopPage /> },
  { path: '/shop/:categorySlug', element: <ShopPage /> },
];

beforeAll(() => {
  window.scrollTo = () => {};
  Element.prototype.scrollIntoView = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('ShopPage', () => {
  it('shows products with server prices and availability', async () => {
    mockApi({ '/products': page([blueDream, blackRose]), '/products/facets': facets, ...taxonomy });
    renderRoutes(routes, { path: '/shop' });

    expect(await screen.findByRole('heading', { level: 1, name: 'All animals' })).toBeInTheDocument();
    const card = (await screen.findByRole('heading', { name: 'Blue Dream' })).closest('article')!;
    expect(within(card).getByText('from €3.90')).toBeInTheDocument();
    expect(within(card).getByText('Grade High')).toBeInTheDocument();
    expect(within(card).getByRole('link')).toHaveAttribute('href', '/products/blue-dream-high');
    const soldOut = screen.getByRole('heading', { name: 'Black Rose' }).closest('article')!;
    expect(within(soldOut).getByText('Sold out')).toBeInTheDocument();
    expect(screen.getByText('2 animals')).toBeInTheDocument();
  });

  it('keeps filters in the URL and sends them to the API', async () => {
    const fetchMock = mockApi({ '/products': page([blueDream]), '/products/facets': facets, ...taxonomy });
    const { router } = renderRoutes(routes, { path: '/shop/neocaridina' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Neocaridina' })).toBeInTheDocument();
    const filters = screen.getByRole('complementary', { name: 'Filters' });
    await userEvent.click(within(filters).getByRole('checkbox', { name: /Blue/ }));

    await waitFor(() => expect(router.state.location.search).toBe('?colour=blue'));
    expect(router.state.location.pathname).toBe('/shop/neocaridina');
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/products?category=neocaridina&colour=blue&size=24',
        expect.anything(),
      ),
    );
    expect(await screen.findByRole('button', { name: 'Remove filter: Blue' })).toBeInTheDocument();

    await userEvent.click(within(filters).getByRole('radio', { name: /In stock/ }));
    await waitFor(() => expect(router.state.location.search).toBe('?colour=blue&availability=in-stock'));
  });

  it('offers to clear filters when nothing matches', async () => {
    mockApi({ '/products': page([]), '/products/facets': facets, ...taxonomy });
    const { router } = renderRoutes(routes, { path: '/shop?colour=black&availability=in-stock' });

    expect(await screen.findByText('No animals match these filters')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });

  it('explains when the shop cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    renderRoutes(routes, { path: '/shop' });
    expect(await screen.findByRole('alert', {}, { timeout: 4000 })).toHaveTextContent(
      'The shop could not be loaded right now.',
    );
  });
});
