import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import type { CartWarning } from '../api/cart';

/** Tells the shopper what the server changed in the cart (stock ran low, item unavailable). */
export function CartWarnings({ warnings }: { warnings: CartWarning[] }) {
  const { t } = useTranslation();
  if (warnings.length === 0) return null;

  const message = (warning: CartWarning) => {
    const name = warning.productName ?? '';
    switch (warning.code) {
      case 'QUANTITY_REDUCED':
        return t('cart.warnings.QUANTITY_REDUCED', { name, count: warning.quantity ?? 0 });
      case 'ITEM_UNAVAILABLE':
        return t('cart.warnings.ITEM_UNAVAILABLE', { name });
      default:
        return t('cart.warnings.ITEM_REMOVED');
    }
  };

  return (
    <Alert tone="info">
      <ul className="space-y-1">
        {warnings.map((warning, index) => (
          <li key={`${warning.code}-${warning.itemId ?? index}`}>{message(warning)}</li>
        ))}
      </ul>
    </Alert>
  );
}
