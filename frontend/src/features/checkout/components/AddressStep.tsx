import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { CheckboxField } from '@/components/ui/Field';
import { AddressBlock, AddressFormDialog, type Address } from '@/features/account';
import { OptionCard } from './OptionCard';

interface AddressStepProps {
  addresses: Address[];
  shippingAddress: Address | undefined;
  billingAddress: Address | undefined;
  billingSame: boolean;
  defaultNames: { firstName: string; lastName: string };
  onShippingChange: (id: string) => void;
  onBillingChange: (id: string) => void;
  onBillingSameChange: (same: boolean) => void;
  onContinue: () => void;
}

/** Step 1: choose the delivery address (and a different billing address, if wanted). */
export function AddressStep({
  addresses,
  shippingAddress,
  billingAddress,
  billingSame,
  defaultNames,
  onShippingChange,
  onBillingChange,
  onBillingSameChange,
  onContinue,
}: AddressStepProps) {
  const { t } = useTranslation();
  const [adding, setAdding] = useState(false);

  return (
    <>
      {addresses.length === 0 ? (
        <p className="text-ink-soft">{t('checkout.address.none')}</p>
      ) : (
        <fieldset>
          <legend className="sr-only">{t('checkout.address.legend')}</legend>
          <div className="grid gap-3 md:grid-cols-2">
            {addresses.map((address) => (
              <OptionCard
                key={address.id}
                name="shipping-address"
                value={address.id}
                checked={address.id === shippingAddress?.id}
                onChange={() => onShippingChange(address.id)}
              >
                {address.label && <span className="mb-1 block font-medium text-ink">{address.label}</span>}
                <AddressBlock address={address} className="text-sm" />
              </OptionCard>
            ))}
          </div>
        </fieldset>
      )}

      <Button variant="secondary" className="mt-4" onClick={() => setAdding(true)}>
        <Plus aria-hidden className="size-4" />
        {t('checkout.address.add')}
      </Button>

      {addresses.length > 0 && (
        <>
          <CheckboxField
            className="mt-6"
            label={t('checkout.address.billingSame')}
            checked={billingSame}
            onChange={(event) => onBillingSameChange(event.target.checked)}
          />
          {!billingSame && (
            <fieldset className="mt-5">
              <legend className="font-sans eyebrow text-ink">{t('checkout.address.billingLegend')}</legend>
              <div className="mt-3 grid gap-3 md:grid-cols-2">
                {addresses.map((address) => (
                  <OptionCard
                    key={address.id}
                    name="billing-address"
                    value={address.id}
                    checked={address.id === billingAddress?.id}
                    onChange={() => onBillingChange(address.id)}
                  >
                    {address.label && (
                      <span className="mb-1 block font-medium text-ink">{address.label}</span>
                    )}
                    <AddressBlock address={address} className="text-sm" />
                  </OptionCard>
                ))}
              </div>
            </fieldset>
          )}
        </>
      )}

      <div className="mt-8">
        <Button size="lg" disabled={!shippingAddress} onClick={onContinue}>
          {t('checkout.continue')}
        </Button>
      </div>

      <AddressFormDialog
        open={adding}
        onOpenChange={setAdding}
        defaultNames={defaultNames}
        onSaved={(address) => onShippingChange(address.id)}
      />
    </>
  );
}
