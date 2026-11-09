import type { RouteObject } from 'react-router';
import { PublicLayout } from './layouts/PublicLayout';
import { NotFoundPage } from './pages/NotFoundPage';
import { RouteErrorPage } from './pages/RouteErrorPage';

/**
 * Route tree. Pages are lazy-loaded so every route ships as its own chunk
 * (the admin panel will be added as a separate lazy branch in Phase 6).
 * The router imports page modules directly; a feature's index.ts only exposes
 * what other features need, so importing it never pulls in unrelated pages.
 */
export const routes: RouteObject[] = [
  {
    element: <PublicLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        index: true,
        lazy: async () => ({ Component: (await import('@/features/home/pages/HomePage')).HomePage }),
      },
      {
        path: 'about',
        lazy: async () => ({ Component: (await import('@/features/content/pages/AboutPage')).AboutPage }),
      },
      {
        path: 'gallery',
        lazy: async () => ({ Component: (await import('@/features/content/pages/GalleryPage')).GalleryPage }),
      },
      {
        path: 'contact',
        lazy: async () => ({ Component: (await import('@/features/content/pages/ContactPage')).ContactPage }),
      },
      {
        path: 'legal/:page',
        lazy: async () => ({ Component: (await import('@/features/content/pages/LegalPage')).LegalPage }),
      },

      // Catalog (Phase 2)
      ...['shop', 'shop/:categorySlug'].map((path) => ({
        path,
        lazy: async () => ({ Component: (await import('@/features/catalog/pages/ShopPage')).ShopPage }),
      })),
      {
        path: 'products/:slug',
        lazy: async () => ({ Component: (await import('@/features/catalog/pages/ProductPage')).ProductPage }),
      },
      {
        path: 'species/:slug',
        lazy: async () => ({ Component: (await import('@/features/catalog/pages/SpeciesPage')).SpeciesPage }),
      },

      // Placeholders until the cart (Phase 4) and accounts (Phase 3) exist.
      {
        path: 'cart',
        lazy: async () => ({ Component: (await import('./pages/ComingSoonPage')).CartComingSoonPage }),
      },
      ...['account/*', 'login', 'register'].map((path) => ({
        path,
        lazy: async () => ({ Component: (await import('./pages/ComingSoonPage')).AccountComingSoonPage }),
      })),

      { path: '*', Component: NotFoundPage },
    ],
  },
];
