import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import type { Address } from '../api/account';

/** A postal address laid out as on a parcel label. */
export function AddressBlock({ address, className }: { address: Address; className?: string }) {
  const { i18n } = useTranslation();
  const country =
    new Intl.DisplayNames([i18n.resolvedLanguage ?? 'en'], { type: 'region' }).of(address.countryCode) ??
    address.countryCode;
  return (
    <address className={cn('leading-relaxed text-ink-soft not-italic', className)}>
      <span className="block text-ink">
        {address.firstName} {address.lastName}
      </span>
      {address.company && <span className="block">{address.company}</span>}
      <span className="block">
        {address.street} {address.houseNumber}
      </span>
      {address.addressLine2 && <span className="block">{address.addressLine2}</span>}
      <span className="block">
        {address.postalCode} {address.city}
      </span>
      <span className="block">{country}</span>
      {address.phone && <span className="mt-1 block text-sm text-muted">{address.phone}</span>}
    </address>
  );
}
