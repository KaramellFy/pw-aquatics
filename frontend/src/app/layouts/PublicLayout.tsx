import { useTranslation } from 'react-i18next';
import { Outlet, ScrollRestoration, useNavigation } from 'react-router';
import { Footer } from '@/components/layout/Footer';
import { Header } from '@/components/layout/Header';
import { CartDrawerProvider } from '@/features/cart';
import { cn } from '@/lib/cn';

export function PublicLayout() {
  const { t } = useTranslation();
  const navigation = useNavigation();
  const navigating = navigation.state === 'loading';

  return (
    <CartDrawerProvider>
      <div className="flex min-h-dvh flex-col">
        <a
          href="#main"
          className="sr-only z-50 rounded-full bg-ink px-5 py-3 text-white focus:not-sr-only focus:fixed focus:top-4 focus:left-4"
        >
          {t('common.skipToContent')}
        </a>
        {/* Thin progress bar while a lazy route chunk loads */}
        <div
          aria-hidden
          className={cn(
            'fixed inset-x-0 top-0 z-50 h-0.5 origin-left bg-brand-400 transition-transform duration-500',
            navigating ? 'scale-x-75' : 'scale-x-0',
          )}
        />
        <Header />
        <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
          <Outlet />
        </main>
        <Footer />
        <ScrollRestoration />
      </div>
    </CartDrawerProvider>
  );
}
