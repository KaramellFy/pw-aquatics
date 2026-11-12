import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { RequireAdmin } from '@/app/guards/RequireAdmin';
import { AdminLayout } from '@/app/layouts/AdminLayout';
import { anna, anonymous } from '@/features/auth/test/fixtures';
import type { Session } from '@/features/auth/api/auth';
import { lastBody, mockApi, problem } from '@/test/mockApi';
import { renderRoutes } from '@/test/render';
import type { AdminProduct, AdminProductSummary } from '../api/products';
import type { AdminCategory } from '../api/taxonomy';
import { AdminInventoryPage } from './AdminInventoryPage';
import { AdminProductCreatePage, AdminProductEditPage } from './AdminProductEditPage';
import { AdminProductsPage } from './AdminProductsPage';

const owner: Session = { authenticated: true, user: { ...anna.user!, role: 'ADMIN' } };
const money = (amount: number) => ({ amount, currency: 'EUR' });

const category: AdminCategory = {
  id: 'c1',
  name: 'Shrimp',
  slug: 'shrimp',
  parentId: null,
  description: null,
  sortOrder: 0,
  active: true,
  productCount: 3,
};

const draft: AdminProduct = {
  id: 'p1',
  version: 4,
  slug: 'blue-dream',
  name: 'Blue Dream',
  status: 'DRAFT',
  categoryId: 'c1',
  speciesId: null,
  gradeId: null,
  colourIds: [],
  shortDescription: null,
  description: null,
  careNotes: null,
  liveAnimal: true,
  featured: false,
  stockUnits: 12,
  lowStockThreshold: 5,
  availableFrom: null,
  taxRateBps: 1900,
  seoTitle: null,
  seoDescription: null,
  availability: 'OUT_OF_STOCK',
  publishedAt: null,
  createdAt: '2026-11-10T10:00:00Z',
  updatedAt: '2026-11-10T10:00:00Z',
  variants: [],
  images: [],
  publishProblems: ['NO_ACTIVE_VARIANT', 'NO_IMAGE'],
};

const image = (id: string, altText: string, sortOrder: number) => ({
  id,
  url: `/media/products/p1/${id}/800.webp`,
  srcSet: `/media/products/p1/${id}/400.webp 400w`,
  altText,
  width: 1600,
  height: 1200,
  sortOrder,
});

const summary: AdminProductSummary = {
  id: 'p1',
  slug: 'blue-dream',
  name: 'Blue Dream',
  status: 'ACTIVE',
  categoryName: 'Shrimp',
  speciesName: 'Neocaridina davidi',
  gradeName: 'High',
  stockUnits: 3,
  lowStockThreshold: 5,
  availability: 'LOW_STOCK',
  priceFrom: money(390),
  variantCount: 2,
  image: null,
  updatedAt: '2026-11-10T10:00:00Z',
};
const page = (items: AdminProductSummary[]) => ({
  items,
  page: 0,
  size: 25,
  totalItems: items.length,
  totalPages: 1,
});

const taxonomy = {
  '/admin/categories': [category],
  '/admin/species': [],
  '/admin/grades': [],
  '/admin/colours': [
    { id: 'k1', name: 'Blue', slug: 'blue', hexCode: '#1565C0', sortOrder: 0, productCount: 1 },
  ],
};

const routes = [
  {
    path: '/admin',
    element: <RequireAdmin />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          { path: 'products', element: <AdminProductsPage /> },
          { path: 'products/new', element: <AdminProductCreatePage /> },
          { path: 'products/:id', element: <AdminProductEditPage /> },
          { path: 'inventory', element: <AdminInventoryPage /> },
        ],
      },
    ],
  },
  { path: '/login', element: <h1>Sign-in page</h1> },
];

beforeAll(() => {
  window.scrollTo = () => {};
});
afterEach(() => vi.unstubAllGlobals());

describe('admin access', () => {
  it('is only for administrators', async () => {
    mockApi({ '/auth/me': anonymous });
    const first = renderRoutes(routes, { path: '/admin/products' });
    expect(await screen.findByRole('heading', { name: 'Sign-in page' })).toBeInTheDocument();
    first.unmount();

    // A signed-in customer is not told that an admin area exists.
    mockApi({ '/auth/me': anna });
    const second = renderRoutes(routes, { path: '/admin/products' });
    expect(await screen.findByText('Error 404')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Admin navigation' })).not.toBeInTheDocument();
    second.unmount();

    mockApi({ '/auth/me': owner, '/admin/products': page([summary]), ...taxonomy });
    renderRoutes(routes, { path: '/admin/products' });
    expect(await screen.findByRole('navigation', { name: 'Admin navigation' })).toBeInTheDocument();
    const row = (await screen.findByRole('link', { name: 'Blue Dream' })).closest('tr')!;
    expect(within(row).getByText('Published')).toBeInTheDocument();
    expect(within(row).getByText('3 animals')).toBeInTheDocument();
    expect(within(row).getByText('€3.90')).toBeInTheDocument();
  });
});

describe('product editor', () => {
  it('creates a draft and opens it for pack sizes and photos', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      ...taxonomy,
      'POST /admin/products': draft,
      '/admin/products/p1': draft,
    });
    const { router } = renderRoutes(routes, { path: '/admin/products/new' });

    await userEvent.type(await screen.findByLabelText('Name'), 'Blue Dream');
    await userEvent.selectOptions(screen.getByLabelText('Category'), 'c1');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Blue' }));
    await userEvent.selectOptions(screen.getByLabelText('VAT rate'), '700');
    await userEvent.click(screen.getByRole('button', { name: 'Create product' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/products/p1'));
    expect(lastBody(fetchMock, 'POST', '/admin/products')).toEqual({
      name: 'Blue Dream',
      categoryId: 'c1',
      colourIds: ['k1'],
      liveAnimal: true,
      featured: false,
      lowStockThreshold: 5,
      taxRateBps: 700,
    });
    // What still blocks publishing comes from the server.
    expect(await screen.findByText('Add at least one pack size that is on sale.')).toBeInTheDocument();
    expect(screen.getByText('Upload at least one photo.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publish' })).toBeDisabled();
  });

  it('reads prices typed as 3,90 and suggests the SKU', async () => {
    const withVariant: AdminProduct = {
      ...draft,
      variants: [
        {
          id: 'v1',
          sku: 'BLUE-DREAM-10',
          name: '10 animals',
          packSize: 10,
          price: money(3490),
          compareAtPrice: money(3900),
          active: true,
          sortOrder: 0,
        },
      ],
      publishProblems: ['NO_IMAGE'],
    };
    const fetchMock = mockApi({
      '/auth/me': owner,
      ...taxonomy,
      '/admin/products/p1': draft,
      'POST /admin/products/p1/variants': withVariant,
    });
    renderRoutes(routes, { path: '/admin/products/p1' });

    await userEvent.click(await screen.findByRole('button', { name: 'Add pack size' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add pack size' });
    await userEvent.type(within(dialog).getByLabelText('Name'), '10 animals');
    await userEvent.clear(within(dialog).getByLabelText('Animals per pack'));
    await userEvent.type(within(dialog).getByLabelText('Animals per pack'), '10');
    expect(within(dialog).getByLabelText('SKU')).toHaveValue('BLUE-DREAM-10');
    await userEvent.type(within(dialog).getByLabelText('Price in € incl. VAT'), '34,90');
    await userEvent.type(within(dialog).getByLabelText('Previous price in € (optional)'), '30');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(
      await within(dialog).findByText('The previous price must be higher than the price.'),
    ).toBeInTheDocument();

    await userEvent.clear(within(dialog).getByLabelText('Previous price in € (optional)'));
    await userEvent.type(within(dialog).getByLabelText('Previous price in € (optional)'), '39');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(lastBody(fetchMock, 'POST', '/admin/products/p1/variants')).toEqual({
      name: '10 animals',
      packSize: 10,
      sku: 'BLUE-DREAM-10',
      priceCents: 3490,
      compareAtPriceCents: 3900,
      active: true,
    });
    const row = screen.getByText('BLUE-DREAM-10').closest('tr')!;
    expect(within(row).getByText('€34.90')).toBeInTheDocument();
    expect(screen.getByText('Upload at least one photo.')).toBeInTheDocument();
  });

  it('saves with the loaded version and explains a conflict', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      ...taxonomy,
      '/admin/products/p1': draft,
      'PUT /admin/products/p1': problem(409, 'CONFLICT'),
    });
    renderRoutes(routes, { path: '/admin/products/p1' });

    const name = await screen.findByLabelText('Name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Blue Dream High');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/Someone else changed this product/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument();
    expect(lastBody(fetchMock, 'PUT', '/admin/products/p1')).toMatchObject({
      name: 'Blue Dream High',
      slug: 'blue-dream',
      version: 4,
    });
  });

  it('uploads photos as files and reorders them with the arrow buttons', async () => {
    const withPhotos: AdminProduct = {
      ...draft,
      images: [image('i1', 'Front', 0), image('i2', 'Side', 1)],
      publishProblems: ['NO_ACTIVE_VARIANT'],
    };
    let uploaded: FormData | undefined;
    const fetchMock = mockApi({
      '/auth/me': owner,
      ...taxonomy,
      '/admin/products/p1': draft,
      'POST /admin/products/p1/images': (_url: URL, init: RequestInit | undefined) => {
        uploaded = init?.body as FormData;
        return withPhotos;
      },
      'PATCH /admin/products/p1/images/order': {
        ...withPhotos,
        images: [image('i2', 'Side', 0), image('i1', 'Front', 1)],
      },
    });
    renderRoutes(routes, { path: '/admin/products/p1' });

    const file = new File(['jpeg bytes'], 'shrimp.jpg', { type: 'image/jpeg' });
    await userEvent.upload(await screen.findByLabelText('Choose photos'), file);

    await screen.findByText('Main photo');
    expect(uploaded).toBeInstanceOf(FormData);
    expect((uploaded!.get('file') as File).name).toBe('shrimp.jpg');
    expect(uploaded!.get('altText')).toBe('Blue Dream');
    expect(screen.getByRole('button', { name: 'Move photo 1 forward' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Move photo 2 forward' }));
    await waitFor(() =>
      expect(lastBody(fetchMock, 'PATCH', '/admin/products/p1/images/order')).toEqual({
        imageIds: ['i2', 'i1'],
      }),
    );
    await waitFor(() => expect(screen.getAllByLabelText('Photo description')[0]).toHaveValue('Side'));
  });
});

describe('stock', () => {
  it('records losses as removals and stocktakes as counted numbers', async () => {
    const fetchMock = mockApi({
      '/auth/me': owner,
      '/admin/products': page([summary]),
      'POST /admin/inventory/p1/adjustments': { productId: 'p1', stockUnits: 1 },
      'PUT /admin/inventory/p1/count': { productId: 'p1', stockUnits: 7 },
    });
    renderRoutes(routes, { path: '/admin/inventory' });

    await userEvent.click(await screen.findByRole('button', { name: 'Change stock of Blue Dream' }));
    let dialog = await screen.findByRole('dialog', { name: 'Change stock: Blue Dream' });
    expect(within(dialog).getByText('Currently 3 animals in stock.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('radio', { name: /Loss/ }));
    await userEvent.type(within(dialog).getByLabelText('Number of animals'), '0');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByText('Enter a whole number above 0.')).toBeInTheDocument();

    await userEvent.clear(within(dialog).getByLabelText('Number of animals'));
    await userEvent.type(within(dialog).getByLabelText('Number of animals'), '2');
    await userEvent.type(within(dialog).getByLabelText('Note (optional)'), 'Moulting problems');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(lastBody(fetchMock, 'POST', '/admin/inventory/p1/adjustments')).toEqual({
      delta: -2,
      reason: 'LOSS',
      note: 'Moulting problems',
    });

    await userEvent.click(screen.getByRole('button', { name: 'Change stock of Blue Dream' }));
    dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('radio', { name: /Stocktake/ }));
    await userEvent.type(within(dialog).getByLabelText('Counted animals'), '7');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(lastBody(fetchMock, 'PUT', '/admin/inventory/p1/count')).toEqual({ countedUnits: 7 }),
    );
  });
});
