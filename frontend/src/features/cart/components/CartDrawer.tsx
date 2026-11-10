import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Price } from '@/components/ui/Price';
import { useCart } from '../api/cart';
import { CartLineItem } from './CartLineItem';

interface CartDrawerState {
  open: () => void;
  close: () => void;
}

const CartDrawerContext = createContext<CartDrawerState | null>(null);

/** Opens the cart drawer, e.g. right after adding a product. */
export function useCartDrawer(): CartDrawerState {
  const context = useContext(CartDrawerContext);
  if (!context) throw new Error('useCartDrawer must be used inside <CartDrawerProvider>');
  return context;
}

/**
 * Provides the cart drawer: a panel that confirms "added to cart" without leaving the page.
 * Whether it is open is plain UI state; its content is the server's cart.
 */
export function CartDrawerProvider({ children }: { children: ReactNode }) {
  const [isOpen, setOpen] = useState(false);
  const open = useCallback(() => setOpen(true), []);
  const close = useCallback(() => setOpen(false), []);
  const value = useMemo(() => ({ open, close }), [open, close]);
  return (
    <CartDrawerContext.Provider value={value}>
      {children}
      <CartDrawer open={isOpen} onOpenChange={setOpen} />
    </CartDrawerContext.Provider>
  );
}

function CartDrawer({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation();
  const cart = useCart();
  const close = () => onOpenChange(false);

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-ink/30 backdrop-blur-sm" />
        <Dialog.Content className="fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col bg-surface shadow-lift focus:outline-none">
          <div className="flex items-center justify-between border-b border-line px-6 py-4">
            <div>
              <Dialog.Title className="font-display text-xl">{t('cart.drawer.title')}</Dialog.Title>
              <Dialog.Description className="mt-0.5 text-sm text-muted">
                {t('cart.drawer.count', { count: cart.data?.itemCount ?? 0 })}
              </Dialog.Description>
            </div>
            <Dialog.Close
              className="-mr-2 inline-flex size-11 items-center justify-center rounded-full hover:bg-brand-50 active:bg-brand-100"
              aria-label={t('common.close')}
            >
              <X className="size-5" strokeWidth={1.5} />
            </Dialog.Close>
          </div>

          <ul className="flex-1 divide-y divide-line overflow-y-auto px-6">
            {cart.data?.items.map((line) => (
              <CartLineItem key={line.id} line={line} compact onNavigate={close} />
            ))}
          </ul>

          {cart.data && (
            <div className="border-t border-line p-6">
              <div className="flex items-baseline justify-between">
                <span className="text-ink-soft">{t('cart.summary.subtotal')}</span>
                <Price value={cart.data.totals.subtotal} className="font-display text-2xl" />
              </div>
              <p className="mt-1 text-sm text-muted">
                {t('cart.summary.shipping')}: {t('cart.summary.shippingNote')}
              </p>
              <ButtonLink to="/cart" size="lg" className="mt-5 w-full" onClick={close}>
                {t('cart.drawer.viewCart')}
              </ButtonLink>
              <Button variant="secondary" className="mt-3 w-full" onClick={close}>
                {t('cart.summary.continue')}
              </Button>
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
