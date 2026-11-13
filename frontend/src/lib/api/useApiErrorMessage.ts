import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from './client';

const KNOWN_CODES = [
  'INVALID_CREDENTIALS',
  'EMAIL_NOT_VERIFIED',
  'ACCOUNT_LOCKED',
  'RATE_LIMITED',
  'TOKEN_INVALID',
  'PASSWORD_TOO_WEAK',
  'CURRENT_PASSWORD_INVALID',
  'EMAIL_TAKEN',
  'EMAIL_UNCHANGED',
  'ADDRESS_LIMIT_REACHED',
  'COUNTRY_INVALID',
  'INSUFFICIENT_STOCK',
  'PRODUCT_UNAVAILABLE',
  'CART_FULL',
  'CART_EMPTY',
  'CART_CHANGED',
  'PRICE_CHANGED',
  'SHIPPING_PAUSED',
  'SHIPPING_METHOD_UNAVAILABLE',
  'PAYMENT_METHOD_UNAVAILABLE',
  'LIVE_ANIMAL_CONDITIONS_REQUIRED',
  'ORDER_NOT_CANCELLABLE',
  'ORDER_ALREADY_PAID',
  'ORDER_NOT_PAYABLE',
  'PAYMENT_PROVIDER_UNAVAILABLE',
  'CONFLICT',
  'VERSION_REQUIRED',
  'SLUG_TAKEN',
  'SLUG_INVALID',
  'SKU_TAKEN',
  'NO_ACTIVE_VARIANT',
  'NO_IMAGE',
  'SPECIES_RESTRICTED',
  'IMAGE_TYPE_UNSUPPORTED',
  'IMAGE_INVALID',
  'IMAGE_TOO_LARGE',
  'IMAGE_TOO_SMALL',
  'IMAGE_LIMIT_REACHED',
  'IMAGE_ORDER_INVALID',
  'LAST_IMAGE',
  'FILE_TOO_LARGE',
  'COMPARE_AT_PRICE_INVALID',
  'VARIANT_INVALID',
  'IN_USE',
  'CATEGORY_CYCLE',
  'RANGE_INVALID',
  'SCIENTIFIC_NAME_TAKEN',
  'ADJUSTMENT_INVALID',
  'STATS_RANGE_INVALID',
  'STOCK_NEGATIVE',
  'FORBIDDEN',
  'VALIDATION_FAILED',
  'NETWORK_ERROR',
] as const;
type KnownCode = (typeof KNOWN_CODES)[number];

function isKnown(code: string): code is KnownCode {
  return (KNOWN_CODES as readonly string[]).includes(code);
}

/** Translates an API error into a message for the user, based on its stable error code. */
export function useApiErrorMessage() {
  const { t } = useTranslation();
  return useCallback(
    (error: unknown): string =>
      error instanceof ApiError && isKnown(error.code)
        ? t(`apiErrors.${error.code}`)
        : t('apiErrors.generic'),
    [t],
  );
}
