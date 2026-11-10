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
