import { screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { mockApi } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import { blueDream, blueDreamDetail } from '../test/fixtures';
import { ProductPage } from './ProductPage';

const routes = [{ path: '/products/:slug', element: <ProductPage /> }];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('ProductPage', () => {
  it('shows product details, pack sizes and care data from the API', async () => {
    mockApi({
      '/products/blue-dream-premium': blueDreamDetail,
      '/products/blue-dream-premium/related': [blueDream],
    });
    renderRoutes(routes, { path: '/products/blue-dream-premium' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Blue Dream' })).toBeInTheDocument();
    expect(screen.getByText('Only a few left')).toBeInTheDocument();
    expect(screen.getByText('Grade Premium')).toBeInTheDocument();

    // The first available pack is selected; sold-out packs cannot be chosen.
    expect(screen.getByRole('radio', { name: /1 animal/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: /10 animals/ })).toBeDisabled();

    // Server-sanitised description and care table.
    expect(screen.getByText('intense blue').tagName).toBe('STRONG');
    expect(screen.getByText('18–28 °C')).toBeInTheDocument();
    expect(screen.getByText('Beginner')).toBeInTheDocument();

    // Ordering is not available yet, and the page says so.
    expect(screen.getByRole('button', { name: 'Add to cart' })).toBeDisabled();
    expect(await screen.findByRole('heading', { name: 'You might also like' })).toBeInTheDocument();

    const jsonLd = document.querySelector('script[type="application/ld+json"]');
    expect(JSON.parse(jsonLd!.textContent!)).toMatchObject({
      '@type': 'Product',
      offers: { lowPrice: '5.90', highPrice: '52.00', priceCurrency: 'EUR' },
    });
  });

  it('shows a friendly message for unknown products', async () => {
    mockApi({});
    renderRoutes(routes, { path: '/products/unknown' });
    expect(await screen.findByRole('heading', { name: 'This animal is not available' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the shop' })).toHaveAttribute('href', '/shop');
  });
});
