import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { CartDrawerProvider } from '@/features/cart';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import { blueDream, blueDreamDetail } from '../test/fixtures';
import { ProductPage } from './ProductPage';

const routes = [
  {
    path: '/products/:slug',
    element: (
      <CartDrawerProvider>
        <ProductPage />
      </CartDrawerProvider>
    ),
  },
];

const cartWithBlueDream = {
  items: [
    {
      id: 'line-1',
      variantId: 'v1',
      productSlug: 'blue-dream-premium',
      productName: 'Blue Dream',
      variantName: '1 animal',
      speciesName: 'Neocaridina davidi',
      gradeName: 'Premium',
      categorySlug: 'neocaridina',
      colourHex: '#1565C0',
      image: null,
      quantity: 2,
      maxQuantity: 6,
      unitPrice: { amount: 590, currency: 'EUR' },
      lineTotal: { amount: 1180, currency: 'EUR' },
      available: true,
    },
  ],
  totals: {
    subtotal: { amount: 1180, currency: 'EUR' },
    total: { amount: 1180, currency: 'EUR' },
    includedTax: { amount: 188, currency: 'EUR' },
  },
  itemCount: 2,
  warnings: [],
};

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

    expect(await screen.findByRole('heading', { name: 'You might also like' })).toBeInTheDocument();

    const jsonLd = document.querySelector('script[type="application/ld+json"]');
    expect(JSON.parse(jsonLd!.textContent!)).toMatchObject({
      '@type': 'Product',
      offers: { lowPrice: '5.90', highPrice: '52.00', priceCurrency: 'EUR' },
    });
  });

  it('adds the chosen quantity to the cart and confirms it in the drawer', async () => {
    const fetchMock = mockApi({
      '/products/blue-dream-premium': blueDreamDetail,
      '/products/blue-dream-premium/related': [],
      '/cart': { items: [], totals: cartWithBlueDream.totals, itemCount: 0, warnings: [] },
      'POST /cart/items': cartWithBlueDream,
    });
    renderRoutes(routes, { path: '/products/blue-dream-premium' });

    await userEvent.click(await screen.findByRole('button', { name: 'Increase quantity' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }));

    // Only an id and a quantity are sent — never a price.
    expect(lastBody(fetchMock, 'POST', '/cart/items')).toEqual({ variantId: 'v1', quantity: 2 });
    const drawer = await screen.findByRole('dialog', { name: 'Added to your cart' });
    expect(within(drawer).getByText('2 items in your cart')).toBeInTheDocument();
    expect(within(drawer).getAllByText('€11.80')).not.toHaveLength(0);
    expect(within(drawer).getByRole('link', { name: 'View cart' })).toHaveAttribute('href', '/cart');
  });

  it('limits the quantity to what the server allows and shows refusals', async () => {
    mockApi({
      '/products/blue-dream-premium': blueDreamDetail,
      '/products/blue-dream-premium/related': [],
      '/cart': { items: [], totals: cartWithBlueDream.totals, itemCount: 0, warnings: [] },
      'POST /cart/items': () => problem(409, 'INSUFFICIENT_STOCK'),
    });
    renderRoutes(routes, { path: '/products/blue-dream-premium' });

    const increase = await screen.findByRole('button', { name: 'Increase quantity' });
    for (let i = 0; i < 5; i++) await userEvent.click(increase);
    expect(increase).toBeDisabled(); // maxQuantity from the API is 6
    expect(screen.getByText('Maximum available: 6')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('not enough of these in stock');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows a friendly message for unknown products', async () => {
    mockApi({});
    renderRoutes(routes, { path: '/products/unknown' });
    expect(await screen.findByRole('heading', { name: 'This animal is not available' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the shop' })).toHaveAttribute('href', '/shop');
  });
});
