import type { RouteObject } from 'react-router';
import { RequireAuth } from './guards/RequireAuth';
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

      // Sign-in (Phase 3)
      {
        path: 'login',
        lazy: async () => ({ Component: (await import('@/features/auth/pages/LoginPage')).LoginPage }),
      },
      {
        path: 'register',
        lazy: async () => ({ Component: (await import('@/features/auth/pages/RegisterPage')).RegisterPage }),
      },
      {
        path: 'verify-email',
        lazy: async () => ({
          Component: (await import('@/features/auth/pages/VerifyEmailPage')).VerifyEmailPage,
        }),
      },
      {
        path: 'forgot-password',
        lazy: async () => ({
          Component: (await import('@/features/auth/pages/ForgotPasswordPage')).ForgotPasswordPage,
        }),
      },
      {
        path: 'reset-password',
        lazy: async () => ({
          Component: (await import('@/features/auth/pages/ResetPasswordPage')).ResetPasswordPage,
        }),
      },

      // Customer account (Phase 3): signed-in users only
      {
        path: 'account',
        element: <RequireAuth />,
        children: [
          {
            lazy: async () => ({ Component: (await import('./layouts/AccountLayout')).AccountLayout }),
            children: [
              {
                index: true,
                lazy: async () => ({
                  Component: (await import('@/features/account/pages/AccountOverviewPage'))
                    .AccountOverviewPage,
                }),
              },
              {
                path: 'addresses',
                lazy: async () => ({
                  Component: (await import('@/features/account/pages/AddressesPage')).AddressesPage,
                }),
              },
              {
                path: 'profile',
                lazy: async () => ({
                  Component: (await import('@/features/account/pages/ProfilePage')).ProfilePage,
                }),
              },
            ],
          },
        ],
      },

      // Cart (Phase 4)
      {
        path: 'cart',
        lazy: async () => ({ Component: (await import('@/features/cart/pages/CartPage')).CartPage }),
      },

      { path: '*', Component: NotFoundPage },
    ],
  },
];
