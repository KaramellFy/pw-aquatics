import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import type { Address } from '@/features/account';
import { anna } from '@/features/auth/test/fixtures';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { Quote, ShippingOption, ShippingOptions } from '../api/checkout';
import { CheckoutPage } from './CheckoutPage';

const money = (amount: number) => ({ amount, currency: 'EUR' });

const home: Address = {
  id: 'a1',
  label: 'Home',
  firstName: 'Anna',
  lastName: 'Schmidt',
  company: null,
  street: 'Hauptstraße',
  houseNumber: '12a',
  addressLine2: null,
  postalCode: '10115',
  city: 'Berlin',
  countryCode: 'DE',
  phone: null,
  defaultShipping: true,
  defaultBilling: true,
};
const office: Address = { ...home, id: 'a2', label: 'Office', city: 'Hamburg', defaultShipping: false };

const line = {
  variantId: 'v1',
  productSlug: 'blue-dream-high',
  productName: 'Blue Dream',
  variantName: '1 animal',
  speciesName: 'Neocaridina davidi',
  gradeName: 'High',
  categorySlug: 'neocaridina',
  colourHex: '#1565C0',
  image: null,
  quantity: 2,
  unitPrice: money(390),
  lineTotal: money(780),
};
const cart = {
  items: [{ ...line, id: 'line-1', maxQuantity: 12, available: true }],
  totals: { subtotal: money(780), total: money(780), includedTax: money(125) },
  itemCount: 2,
  warnings: [],
};
const emptyCart = { ...cart, items: [], itemCount: 0, totals: { ...cart.totals, subtotal: money(0) } };

const dhl: ShippingOption = {
  code: 'DHL_LIVE_DE',
  name: 'DHL Express – Germany',
  description: 'Insulated box.',
  carrier: 'DHL',
  price: money(990),
  freeFrom: null,
  plannedDispatchDate: '2026-11-16',
  estimatedDeliveryFrom: '2026-11-17',
  estimatedDeliveryTo: '2026-11-17',
};
const options: ShippingOptions = { methods: [dhl], paused: false, pauseMessage: null, pausedUntil: null };

function quoteOf(total: number): Quote {
  return {
    items: [line],
    totals: {
      subtotal: money(780),
      shipping: money(total - 780),
      total: money(total),
      includedTax: money(283),
    },
    shippingMethod: dhl,
    containsLiveAnimals: true,
    paymentMethods: ['BANK_TRANSFER'],
  };
}

const placed = {
  orderNumber: 'PW-2026-000042',
  status: 'PENDING_PAYMENT',
  paymentStatus: 'UNPAID',
  paymentMethod: 'BANK_TRANSFER',
  total: money(1770),
  paymentDueAt: '2026-11-17T22:59:59Z',
  paymentInstructions: null,
};

const routes = [
  { path: '/checkout', element: <CheckoutPage /> },
  { path: '/checkout/complete/:orderNumber', element: <h1>Order placed</h1> },
  { path: '/checkout/pay/:orderNumber', element: <h1>Card payment</h1> },
  { path: '/cart', element: <h1>Cart page</h1> },
];

function api(overrides: Record<string, unknown> = {}) {
  return mockApi({
    '/auth/me': anna,
    '/cart': cart,
    '/account/addresses': [home, office],
    '/checkout/shipping-methods': options,
    'POST /checkout/quote': quoteOf(1770),
    'POST /checkout/orders': placed,
    ...overrides,
  });
}

/** Idempotency keys of all order requests, in the order they were sent. */
function orderKeys(fetchMock: ReturnType<typeof mockApi>): string[] {
  return fetchMock.mock.calls
    .filter(([input, init]) => String(input) === '/api/v1/checkout/orders' && init?.method === 'POST')
    .map(([, init]) => (init?.headers as Record<string, string>)['Idempotency-Key'] ?? '');
}

async function goToReview() {
  await userEvent.click(await screen.findByRole('button', { name: 'Continue' }));
  await screen.findByRole('radio', { name: /DHL Express – Germany/ });
  await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
  return screen.findByRole('button', { name: 'Order and pay' });
}

async function acceptConditions() {
  await userEvent.click(screen.getByRole('checkbox', { name: /I have read and accept/ }));
  await userEvent.click(screen.getByRole('checkbox', { name: /live animals are shipped only/ }));
}

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('CheckoutPage', () => {
  it('leads through address, shipping and review and orders with ids only', async () => {
    const fetchMock = api();
    const { router } = renderRoutes(routes, { path: '/checkout' });

    // Step 1: the default delivery address is preselected.
    expect(await screen.findByRole('radio', { name: /Home/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: /Office/ })).not.toBeChecked();
    const summary = screen.getByRole('region', { name: 'Your order' });
    expect(within(summary).getByText('Calculated in the next step')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    // Step 2: methods, prices and dates as the server sent them.
    const method = await screen.findByRole('radio', { name: /DHL Express – Germany/ });
    expect(method).toBeChecked();
    const card = method.closest('label')!;
    expect(within(card).getByText('€9.90')).toBeInTheDocument();
    expect(within(card).getByText(/Earliest dispatch: Monday, 16 November 2026/)).toBeInTheDocument();
    expect(within(card).getByText(/Expected delivery: Tuesday, 17 November 2026/)).toBeInTheDocument();
    expect(
      String(fetchMock.mock.calls.find(([input]) => String(input).includes('shipping-methods'))![0]),
    ).toBe('/api/v1/checkout/shipping-methods?country=DE');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    // Step 3: the quote's totals; ordering needs both confirmations.
    const submit = await screen.findByRole('button', { name: 'Order and pay' });
    expect(lastBody(fetchMock, 'POST', '/checkout/quote')).toEqual({
      shippingAddressId: 'a1',
      shippingMethodCode: 'DHL_LIVE_DE',
    });
    expect(within(summary).getByText('€17.70')).toBeInTheDocument();
    expect(within(summary).getByText('incl. €2.83 VAT')).toBeInTheDocument();
    await userEvent.click(submit);
    expect(await screen.findByText('Please accept the terms to place your order.')).toBeInTheDocument();
    expect(screen.getByText('Please confirm the conditions for live animals.')).toBeInTheDocument();
    expect(orderKeys(fetchMock)).toHaveLength(0);

    await acceptConditions();
    await userEvent.type(screen.getByLabelText('Note for your order (optional)'), 'Ring twice');
    await userEvent.click(submit);

    expect(await screen.findByRole('heading', { name: 'Order placed' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/checkout/complete/PW-2026-000042');
    // No items and no prices: only ids, the method and the total the customer saw.
    expect(lastBody(fetchMock, 'POST', '/checkout/orders')).toEqual({
      shippingAddressId: 'a1',
      shippingMethodCode: 'DHL_LIVE_DE',
      paymentMethod: 'BANK_TRANSFER',
      customerNote: 'Ring twice',
      acceptedTerms: true,
      acceptedLiveAnimalConditions: true,
      expectedTotal: money(1770),
    });
    expect(orderKeys(fetchMock)[0]).toMatch(/^[A-Za-z0-9_-]{16,100}$/);
  });

  it('offers the payment methods the server allows and goes on to pay by card', async () => {
    const fetchMock = api({
      'POST /checkout/quote': { ...quoteOf(1770), paymentMethods: ['STRIPE', 'BANK_TRANSFER'] },
      'POST /checkout/orders': (_url: URL, init: RequestInit | undefined) => ({
        ...placed,
        paymentMethod: (JSON.parse(String(init?.body)) as { paymentMethod: string }).paymentMethod,
      }),
    });
    const { router } = renderRoutes(routes, { path: '/checkout' });
    await goToReview();

    const methods = screen.getByRole('group', { name: 'Payment method' });
    const card = within(methods).getByRole('radio', { name: /Card/ });
    expect(card).toBeChecked();
    expect(within(methods).getByRole('radio', { name: /Bank transfer/ })).not.toBeChecked();
    await acceptConditions();
    await userEvent.click(screen.getByRole('button', { name: 'Order and pay' }));

    expect(await screen.findByRole('heading', { name: 'Card payment' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/checkout/pay/PW-2026-000042');
    expect(lastBody(fetchMock, 'POST', '/checkout/orders')).toMatchObject({ paymentMethod: 'STRIPE' });
  });

  it('sends bank transfer when the customer chooses it', async () => {
    const fetchMock = api({
      'POST /checkout/quote': { ...quoteOf(1770), paymentMethods: ['STRIPE', 'BANK_TRANSFER'] },
    });
    renderRoutes(routes, { path: '/checkout' });
    await goToReview();
    await userEvent.click(screen.getByRole('radio', { name: /Bank transfer/ }));
    await acceptConditions();
    await userEvent.click(screen.getByRole('button', { name: 'Order and pay' }));

    await screen.findByRole('heading', { name: 'Order placed' });
    expect(lastBody(fetchMock, 'POST', '/checkout/orders')).toMatchObject({ paymentMethod: 'BANK_TRANSFER' });
  });

  it('sends a different billing address only when the customer chooses one', async () => {
    const fetchMock = api();
    renderRoutes(routes, { path: '/checkout' });

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Bill to the same address' }));
    const billing = screen.getByRole('group', { name: 'Billing address' });
    await userEvent.click(within(billing).getByRole('radio', { name: /Office/ }));
    await goToReview();
    await acceptConditions();
    await userEvent.click(screen.getByRole('button', { name: 'Order and pay' }));

    await screen.findByRole('heading', { name: 'Order placed' });
    expect(lastBody(fetchMock, 'POST', '/checkout/orders')).toMatchObject({
      shippingAddressId: 'a1',
      billingAddressId: 'a2',
    });
  });

  it('repeats a lost request with the same key and shows a changed total before ordering again', async () => {
    let attempt = 0;
    let total = 1770;
    const fetchMock = api({
      'POST /checkout/quote': () => quoteOf(total),
      'POST /checkout/orders': () => {
        attempt += 1;
        if (attempt === 1) throw new TypeError('Failed to fetch');
        if (attempt === 2) {
          total = 1870;
          return problem(409, 'PRICE_CHANGED');
        }
        return placed;
      },
    });
    renderRoutes(routes, { path: '/checkout' });
    const submit = await goToReview();
    await acceptConditions();

    // The answer got lost: nothing is known about the order, so the retry reuses the key.
    await userEvent.click(submit);
    expect(await screen.findByText(/No connection to the server/)).toBeInTheDocument();
    await userEvent.click(submit);
    expect(await screen.findByText(/The total of your order has changed/)).toBeInTheDocument();
    await waitFor(() => expect(orderKeys(fetchMock)).toHaveLength(2));
    expect(orderKeys(fetchMock)[1]).toBe(orderKeys(fetchMock)[0]);

    // The server refused: the new total is shown and the next attempt is a new one.
    const summary = screen.getByRole('region', { name: 'Your order' });
    expect(await within(summary).findByText('€18.70')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Order and pay' }));
    await screen.findByRole('heading', { name: 'Order placed' });
    expect(orderKeys(fetchMock)[2]).not.toBe(orderKeys(fetchMock)[1]);
    expect(lastBody(fetchMock, 'POST', '/checkout/orders')).toMatchObject({ expectedTotal: money(1870) });
  });

  it('sends the customer back to the cart when it no longer fits the stock', async () => {
    api({ 'POST /checkout/orders': problem(409, 'CART_CHANGED') });
    renderRoutes(routes, { path: '/checkout' });
    const submit = await goToReview();
    await acceptConditions();
    await userEvent.click(submit);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('no longer available as requested');
    await userEvent.click(within(alert).getByRole('link', { name: 'Review cart' }));
    expect(await screen.findByRole('heading', { name: 'Cart page' })).toBeInTheDocument();
  });

  it('explains when there is no delivery to the country or shipping is paused', async () => {
    let answer: ShippingOptions = { ...options, methods: [] };
    api({ '/checkout/shipping-methods': () => answer });
    const first = renderRoutes(routes, { path: '/checkout' });
    await userEvent.click(await screen.findByRole('button', { name: 'Continue' }));
    expect(await screen.findByText(/We do not deliver to Germany yet/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Continue' })).not.toBeInTheDocument();
    first.unmount();

    answer = {
      methods: [],
      paused: true,
      pauseMessage: 'Heat wave until further notice.',
      pausedUntil: null,
    };
    renderRoutes(routes, { path: '/checkout' });
    await userEvent.click(await screen.findByRole('button', { name: 'Continue' }));
    expect(await screen.findByText(/Shipping of live animals is paused at the moment/)).toBeInTheDocument();
    expect(screen.getByText('Heat wave until further notice.')).toBeInTheDocument();
  });

  it('has nothing to check out with an empty cart', async () => {
    const fetchMock = api({ '/cart': emptyCart });
    renderRoutes(routes, { path: '/checkout' });

    expect(await screen.findByRole('heading', { name: 'Your cart is empty' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Browse the shop' })).toHaveAttribute('href', '/shop');
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/checkout/'))).toBe(false);
  });
});
