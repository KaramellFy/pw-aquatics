import { Navigate, type RouteObject } from 'react-router';
import { RequireAdmin } from './guards/RequireAdmin';
import { RequireAuth } from './guards/RequireAuth';
import { PublicLayout } from './layouts/PublicLayout';
import { NotFoundPage } from './pages/NotFoundPage';
import { RouteErrorPage } from './pages/RouteErrorPage';

/**
 * Route tree. Pages are lazy-loaded so every route ships as its own chunk
 * (the admin panel is a separate lazy branch with its own layout).
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
                path: 'orders',
                lazy: async () => ({
                  Component: (await import('@/features/account/pages/OrdersPage')).OrdersPage,
                }),
              },
              {
                path: 'orders/:orderNumber',
                lazy: async () => ({
                  Component: (await import('@/features/account/pages/OrderDetailPage')).OrderDetailPage,
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

      // Checkout (Phase 5, card payment from Phase 9): signed-in customers only
      {
        path: 'checkout',
        element: <RequireAuth />,
        children: [
          {
            index: true,
            lazy: async () => ({
              Component: (await import('@/features/checkout/pages/CheckoutPage')).CheckoutPage,
            }),
          },
          {
            path: 'pay/:orderNumber',
            lazy: async () => ({
              Component: (await import('@/features/checkout/pages/CardPaymentPage')).CardPaymentPage,
            }),
          },
          {
            path: 'complete/:orderNumber',
            lazy: async () => ({
              Component: (await import('@/features/checkout/pages/OrderConfirmationPage'))
                .OrderConfirmationPage,
            }),
          },
        ],
      },

      { path: '*', Component: NotFoundPage },
    ],
  },

  // Admin panel (Phase 6, dashboard from Phase 8): administrators only, own layout, own chunks
  {
    path: 'admin',
    element: <RequireAdmin />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        lazy: async () => ({ Component: (await import('./layouts/AdminLayout')).AdminLayout }),
        children: [
          {
            index: true,
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminDashboardPage')).AdminDashboardPage,
            }),
          },
          {
            path: 'products',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminProductsPage')).AdminProductsPage,
            }),
          },
          {
            path: 'products/new',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminProductEditPage')).AdminProductCreatePage,
            }),
          },
          {
            path: 'products/:id',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminProductEditPage')).AdminProductEditPage,
            }),
          },
          {
            path: 'inventory',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminInventoryPage')).AdminInventoryPage,
            }),
          },
          {
            path: 'inventory/:productId',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminStockHistoryPage')).AdminStockHistoryPage,
            }),
          },
          {
            path: 'categories',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminTaxonomyPages')).AdminCategoriesPage,
            }),
          },
          {
            path: 'species',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminTaxonomyPages')).AdminSpeciesPage,
            }),
          },
          {
            path: 'species/:id',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminSpeciesEditPage')).AdminSpeciesEditPage,
            }),
          },
          {
            path: 'colours',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminTaxonomyPages')).AdminColoursPage,
            }),
          },
          {
            path: 'grades',
            lazy: async () => ({
              Component: (await import('@/features/admin/pages/AdminTaxonomyPages')).AdminGradesPage,
            }),
          },
          { path: '*', element: <Navigate to="/admin" replace /> },
        ],
      },
    ],
  },
];
