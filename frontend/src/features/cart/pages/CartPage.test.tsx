import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { Cart, CartLine, CouponState } from '../api/cart';
import { CartPage } from './CartPage';

const money = (amount: number) => ({ amount, currency: 'EUR' });

const blueDream: CartLine = {
  id: 'line-1',
  variantId: 'v1',
  productSlug: 'blue-dream-high',
  productName: 'Blue Dream',
  variantName: '1 animal',
  speciesName: 'Neocaridina davidi',
  gradeName: 'High',
  categorySlug: 'neocaridina',
  colourHex: '#1565C0',
  image: null,
  quantity: 3,
  maxQuantity: 12,
  unitPrice: money(390),
  lineTotal: money(1170),
  available: true,
};
const soldOut: CartLine = {
  ...blueDream,
  id: 'line-2',
  productSlug: 'black-rose-premium',
  productName: 'Black Rose',
  quantity: 1,
  maxQuantity: 0,
  unitPrice: money(490),
  lineTotal: money(490),
  available: false,
};

function cartOf(items: CartLine[], total: number, warnings: Cart['warnings'] = []): Cart {
  return {
    items,
    totals: { subtotal: money(total), discount: money(0), total: money(total), includedTax: money(187) },
    itemCount: items.filter((line) => line.available).reduce((sum, line) => sum + line.quantity, 0),
    warnings,
    coupon: null,
  };
}

const routes = [{ path: '/cart', element: <CartPage /> }];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('CartPage', () => {
  it('shows lines, totals and warnings exactly as the server calculated them', async () => {
    mockApi({
      '/cart': cartOf([blueDream, soldOut], 1170, [
        { code: 'QUANTITY_REDUCED', itemId: 'line-1', productName: 'Blue Dream', quantity: 3 },
        { code: 'ITEM_UNAVAILABLE', itemId: 'line-2', productName: 'Black Rose', quantity: null },
      ]),
    });
    renderRoutes(routes, { path: '/cart' });

    const line = (await screen.findByRole('link', { name: 'Blue Dream' })).closest('li')!;
    expect(within(line).getByText('€11.70')).toBeInTheDocument();
    expect(within(line).getByText('€3.90 each')).toBeInTheDocument();
    expect(within(line).getByRole('group', { name: 'Quantity of Blue Dream' })).toHaveTextContent('3');

    const unavailable = screen.getByRole('link', { name: 'Black Rose' }).closest('li')!;
    expect(within(unavailable).getByText('Currently not available')).toBeInTheDocument();
    expect(within(unavailable).queryByRole('group')).not.toBeInTheDocument();

    expect(screen.getByText(/Only 3 of “Blue Dream” available/)).toBeInTheDocument();
    expect(screen.getByText(/“Black Rose” is currently not available/)).toBeInTheDocument();

    const summary = screen.getByRole('region', { name: 'Summary' });
    expect(within(summary).getAllByText('€11.70')).toHaveLength(2); // subtotal and total
    expect(within(summary).getByText('incl. €1.87 VAT')).toBeInTheDocument();
    expect(within(summary).getByText('Calculated at checkout')).toBeInTheDocument();
    expect(within(summary).getByRole('link', { name: 'Checkout' })).toHaveAttribute('href', '/checkout');
  });

  it('changes quantities and removes lines through the API', async () => {
    const fetchMock = mockApi({
      '/cart': cartOf([blueDream], 1170),
      'PATCH /cart/items/line-1': cartOf([{ ...blueDream, quantity: 4, lineTotal: money(1560) }], 1560),
      'DELETE /cart/items/line-1': cartOf([], 0),
    });
    renderRoutes(routes, { path: '/cart' });

    await userEvent.click(await screen.findByRole('button', { name: 'Increase quantity' }));
    expect(lastBody(fetchMock, 'PATCH', '/cart/items/line-1')).toEqual({ quantity: 4 });
    // The new totals come from the response, not from a calculation in the browser.
    await waitFor(() => expect(screen.getAllByText('€15.60')).not.toHaveLength(0));

    await userEvent.click(screen.getByRole('button', { name: 'Remove Blue Dream from cart' }));
    expect(await screen.findByText('Your cart is empty')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Browse the shop' })).toHaveAttribute('href', '/shop');
  });

  it('enters a coupon code and shows the discount the server granted', async () => {
    const coupon: CouponState = {
      code: 'SPRING10',
      description: 'Spring sale',
      type: 'PERCENTAGE',
      percentage: 1000,
      amount: null,
      maxDiscount: null,
      minOrder: null,
      applied: true,
      problem: null,
      saving: money(117),
    };
    let attempts = 0;
    const fetchMock = mockApi({
      '/cart': cartOf([blueDream], 1170),
      'PUT /cart/coupon': () =>
        ++attempts === 1
          ? problem(422, 'COUPON_EXPIRED')
          : {
              ...cartOf([blueDream], 1170),
              totals: {
                subtotal: money(1170),
                discount: money(117),
                total: money(1053),
                includedTax: money(168),
              },
              coupon,
            },
      'DELETE /cart/coupon': cartOf([blueDream], 1170),
    });
    renderRoutes(routes, { path: '/cart' });

    const summary = await screen.findByRole('region', { name: 'Summary' });
    const field = within(summary).getByLabelText('Coupon code');
    await userEvent.click(within(summary).getByRole('button', { name: 'Apply' }));
    expect(within(summary).getByText('Enter a code.')).toBeInTheDocument();

    await userEvent.type(field, 'spring10');
    await userEvent.click(within(summary).getByRole('button', { name: 'Apply' }));
    expect(await within(summary).findByText('This code has expired.')).toBeInTheDocument();

    await userEvent.click(within(summary).getByRole('button', { name: 'Apply' }));
    expect(await within(summary).findByText('SPRING10')).toBeInTheDocument();
    expect(lastBody(fetchMock, 'PUT', '/cart/coupon')).toEqual({ code: 'spring10' });
    expect(within(summary).getByText('Coupon SPRING10')).toBeInTheDocument();
    expect(within(summary).getByText('−€1.17')).toBeInTheDocument();
    expect(within(summary).getByText('You save €1.17.')).toBeInTheDocument();
    expect(within(summary).getByText('€10.53')).toBeInTheDocument();

    await userEvent.click(within(summary).getByRole('button', { name: 'Remove coupon SPRING10' }));
    expect(await within(summary).findByLabelText('Coupon code')).toBeInTheDocument();
  });

  it('keeps a coupon that stopped applying and says why', async () => {
    mockApi({
      '/cart': {
        ...cartOf([blueDream], 1170),
        coupon: {
          code: 'FIVEOFF',
          description: null,
          type: 'FIXED_AMOUNT',
          percentage: null,
          amount: money(500),
          maxDiscount: null,
          minOrder: money(3000),
          applied: false,
          problem: 'COUPON_MIN_ORDER_NOT_MET',
          saving: money(0),
        },
      },
    });
    renderRoutes(routes, { path: '/cart' });

    const summary = await screen.findByRole('region', { name: 'Summary' });
    expect(await within(summary).findByText('Applies from an order value of €30.00.')).toBeInTheDocument();
    expect(within(summary).getByText('€5.00 off')).toBeInTheDocument();
    expect(within(summary).queryByText(/^Coupon FIVEOFF/)).not.toBeInTheDocument();
  });

  it('explains when the cart cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    renderRoutes(routes, { path: '/cart' });
    expect(await screen.findByRole('alert', {}, { timeout: 4000 })).toHaveTextContent(
      'Your cart could not be loaded right now.',
    );
  });
});
