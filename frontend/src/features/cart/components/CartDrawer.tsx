import { AnimatePresence } from 'motion/react';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Price } from '@/components/ui/Price';
import { Sheet } from '@/components/ui/Sheet';
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
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={t('cart.drawer.title')}
      description={t('cart.drawer.count', { count: cart.data?.itemCount ?? 0 })}
    >
      <ul className="flex-1 divide-y divide-line overflow-y-auto px-6">
        <AnimatePresence initial={false}>
          {cart.data?.items.map((line, index) => (
            <CartLineItem key={line.id} line={line} compact onNavigate={close} index={index} animateIn />
          ))}
        </AnimatePresence>
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
    </Sheet>
  );
}
