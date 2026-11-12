import { Check, LoaderCircle, ShoppingBag } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CrossfadeIcon } from '@/components/motion/CrossfadeIcon';
import { flyToCart } from '@/components/motion/flyToCart';
import { SwapText } from '@/components/motion/SwapText';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { QuantityStepper } from '@/components/ui/QuantityStepper';
import { useAddToCart, useCartDrawer } from '@/features/cart';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import type { ProductDetail } from '../api/catalog';

type Variant = ProductDetail['variants'][number];

/** How long the button says "Added" before it is ready again. */
const ADDED_FOR_MS = 1800;

/**
 * Quantity + "Add to cart" for the selected pack size. Sends only the variant id and a
 * quantity; the server decides about price and stock and may refuse.
 *
 * On success the bag icon turns into a check and the button pulses once, a dot travels in an
 * arc to the cart icon, and the cart drawer opens when it lands. The button is never disabled
 * while adding (it is marked busy instead), so keyboard focus stays on it and returns to it
 * when the drawer closes.
 */
export function AddToCart({ variant, productName }: { variant: Variant; productName: string }) {
  const { t } = useTranslation();
  const add = useAddToCart();
  const drawer = useCartDrawer();
  const errorMessage = useApiErrorMessage();
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(0);
  const button = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (added === 0) return;
    const timer = window.setTimeout(() => setAdded(0), ADDED_FOR_MS);
    return () => window.clearTimeout(timer);
  }, [added]);

  if (!variant.available) {
    return (
      <Button size="lg" className="w-full" disabled>
        {t('product.soldOut')}
      </Button>
    );
  }

  const state = add.isPending ? 'adding' : added > 0 ? 'added' : 'idle';
  const label = { idle: t('product.addToCart'), adding: t('product.adding'), added: t('product.added') }[
    state
  ];
  const icon = {
    idle: <ShoppingBag className="size-4" />,
    adding: <LoaderCircle className="size-4 animate-spin" />,
    added: <Check className="size-4" strokeWidth={2.5} />,
  }[state];

  const submit = () => {
    if (add.isPending) return;
    add.mutate(
      { variantId: variant.id, quantity },
      {
        onSuccess: async () => {
          setAdded((count) => count + 1);
          if (button.current) await flyToCart(button.current);
          drawer.open();
        },
      },
    );
  };

  return (
    <div className="space-y-4">
      {add.isError && <Alert tone="error">{errorMessage(add.error)}</Alert>}
      <div className="flex flex-wrap items-center gap-3">
        <QuantityStepper
          value={quantity}
          max={variant.maxQuantity}
          disabled={add.isPending}
          label={t('cart.quantity.labelFor', { name: productName })}
          onChange={setQuantity}
        />
        <Button
          ref={button}
          size="lg"
          className="relative min-w-48 flex-1"
          aria-disabled={add.isPending || undefined}
          aria-busy={add.isPending || undefined}
          onClick={submit}
        >
          {/* Re-keyed per success so the confirmation ring pulses each time. */}
          {added > 0 && (
            <span key={added} aria-hidden className="absolute inset-0 animate-confirm rounded-full" />
          )}
          <CrossfadeIcon stateKey={state}>{icon}</CrossfadeIcon>
          <SwapText value={state}>{label}</SwapText>
        </Button>
      </div>
      {quantity >= variant.maxQuantity && (
        <p className="text-sm text-muted">{t('cart.quantity.max', { count: variant.maxQuantity })}</p>
      )}
    </div>
  );
}
