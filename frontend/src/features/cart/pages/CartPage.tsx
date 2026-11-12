import { AnimatePresence } from 'motion/react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useCart, useClearCart } from '../api/cart';
import { CartLineItem } from '../components/CartLineItem';
import { CartSummary } from '../components/CartSummary';
import { CartWarnings } from '../components/CartWarnings';

export function CartPage() {
  const { t } = useTranslation();
  const cart = useCart();
  const clear = useClearCart();

  return (
    <>
      <PageMeta title={t('cart.metaTitle')} noIndex />
      <Container className="pt-10 pb-24 sm:pt-14">
        <h1 className="text-headline">{t('cart.title')}</h1>

        {cart.isPending && (
          <p role="status" className="mt-10 text-muted">
            {t('common.loading')}
          </p>
        )}
        {cart.isError && !cart.data && (
          <Alert tone="error" className="mt-10">
            <p>{t('cart.loadError')}</p>
            <Button variant="secondary" size="sm" className="mt-4" onClick={() => void cart.refetch()}>
              {t('shop.error.retry')}
            </Button>
          </Alert>
        )}

        {cart.data && cart.data.items.length === 0 && (
          <div className="mt-10 rounded-card border border-line bg-mist px-6 py-16 text-center">
            <h2 className="text-title">{t('cart.empty.title')}</h2>
            <p className="mx-auto mt-3 max-w-md text-ink-soft">{t('cart.empty.text')}</p>
            <ButtonLink to="/shop" className="mt-8">
              {t('cart.empty.cta')}
            </ButtonLink>
          </div>
        )}

        {cart.data && cart.data.items.length > 0 && (
          <div className="mt-10 grid gap-10 lg:grid-cols-12 lg:gap-12">
            <div className="lg:col-span-7">
              <CartWarnings warnings={cart.data.warnings} />
              <ul className="divide-y divide-line border-y border-line">
                <AnimatePresence initial={false}>
                  {cart.data.items.map((line) => (
                    <CartLineItem key={line.id} line={line} />
                  ))}
                </AnimatePresence>
              </ul>
              <Button
                variant="ghost"
                className="mt-4 text-ink-soft"
                disabled={clear.isPending}
                onClick={() => clear.mutate(undefined)}
              >
                {t('cart.clear')}
              </Button>
            </div>
            <div className="lg:col-span-5">
              <div className="lg:sticky lg:top-28">
                <CartSummary cart={cart.data} />
              </div>
            </div>
          </div>
        )}
      </Container>
    </>
  );
}
