import { ShoppingBag } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { QuantityStepper } from '@/components/ui/QuantityStepper';
import { useAddToCart, useCartDrawer } from '@/features/cart';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import type { ProductDetail } from '../api/catalog';

type Variant = ProductDetail['variants'][number];

/**
 * Quantity + "Add to cart" for the selected pack size. Sends only the variant id and a
 * quantity; the server decides about price and stock and may refuse.
 */
export function AddToCart({ variant, productName }: { variant: Variant; productName: string }) {
  const { t } = useTranslation();
  const add = useAddToCart();
  const drawer = useCartDrawer();
  const errorMessage = useApiErrorMessage();
  const [quantity, setQuantity] = useState(1);

  if (!variant.available) {
    return (
      <Button size="lg" className="w-full" disabled>
        {t('product.soldOut')}
      </Button>
    );
  }

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
          size="lg"
          className="min-w-48 flex-1"
          disabled={add.isPending}
          onClick={() => add.mutate({ variantId: variant.id, quantity }, { onSuccess: drawer.open })}
        >
          <ShoppingBag aria-hidden className="size-4" />
          {add.isPending ? t('product.adding') : t('product.addToCart')}
        </Button>
      </div>
      {quantity >= variant.maxQuantity && (
        <p className="text-sm text-muted">{t('cart.quantity.max', { count: variant.maxQuantity })}</p>
      )}
    </div>
  );
}
