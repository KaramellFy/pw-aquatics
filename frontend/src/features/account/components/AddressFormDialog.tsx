import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { SelectField, TextField } from '@/components/ui/Field';
import { shopConfig } from '@/config/shop';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import { useSaveAddress, type Address, type AddressInput } from '../api/account';

const PHONE = /^[0-9+()/ .-]*$/;

/** Mirrors the server's limits for instant feedback; the server validates again. */
function addressSchema(t: TFunction) {
  const required = (max: number) =>
    z.string().trim().min(1, t('auth.validation.required')).max(max, t('auth.validation.tooLong'));
  const optional = (max: number) => z.string().trim().max(max, t('auth.validation.tooLong'));
  return z.object({
    label: optional(50),
    firstName: required(100),
    lastName: required(100),
    company: optional(150),
    street: required(150),
    houseNumber: required(20),
    addressLine2: optional(150),
    postalCode: required(20),
    city: required(100),
    countryCode: z.string().length(2),
    phone: optional(40).regex(PHONE, t('auth.validation.phoneInvalid')),
  });
}
type AddressValues = z.infer<ReturnType<typeof addressSchema>>;
const FIELDS = [
  'label',
  'firstName',
  'lastName',
  'company',
  'street',
  'houseNumber',
  'addressLine2',
  'postalCode',
  'city',
  'countryCode',
  'phone',
] as const;

function toValues(
  address: Address | undefined,
  names: { firstName: string; lastName: string },
): AddressValues {
  return {
    label: address?.label ?? '',
    firstName: address?.firstName ?? names.firstName,
    lastName: address?.lastName ?? names.lastName,
    company: address?.company ?? '',
    street: address?.street ?? '',
    houseNumber: address?.houseNumber ?? '',
    addressLine2: address?.addressLine2 ?? '',
    postalCode: address?.postalCode ?? '',
    city: address?.city ?? '',
    countryCode: address?.countryCode ?? shopConfig.defaultCountry,
    phone: address?.phone ?? '',
  };
}

/** Empty optional fields are left out of the request. */
function toInput(values: AddressValues): AddressInput {
  return {
    ...values,
    label: values.label || undefined,
    company: values.company || undefined,
    addressLine2: values.addressLine2 || undefined,
    phone: values.phone || undefined,
  };
}

interface AddressFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The address to edit; omit to add a new one. */
  address?: Address;
  /** Prefills the name fields of a new address. */
  defaultNames: { firstName: string; lastName: string };
}

export function AddressFormDialog({ open, onOpenChange, address, defaultNames }: AddressFormDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={address ? t('address.form.editTitle') : t('address.form.addTitle')}
    >
      {/* Mounted only while open, so the form always starts from the current address. */}
      {open && (
        <AddressForm address={address} defaultNames={defaultNames} onDone={() => onOpenChange(false)} />
      )}
    </Dialog>
  );
}

function AddressForm({
  address,
  defaultNames,
  onDone,
}: {
  address?: Address;
  defaultNames: { firstName: string; lastName: string };
  onDone: () => void;
}) {
  const { t, i18n } = useTranslation();
  const save = useSaveAddress();
  const errorMessage = useApiErrorMessage();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => addressSchema(t), [t, i18n.language]);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<AddressValues>({
    resolver: zodResolver(schema),
    defaultValues: toValues(address, defaultNames),
  });

  const countries = useMemo(() => {
    const names = new Intl.DisplayNames([i18n.resolvedLanguage ?? 'en'], { type: 'region' });
    const codes = new Set<string>(shopConfig.addressCountries);
    if (address) codes.add(address.countryCode); // keep an existing country selectable
    return [...codes]
      .map((code) => ({ code, name: names.of(code) ?? code }))
      .sort((a, b) => a.name.localeCompare(b.name, i18n.resolvedLanguage));
  }, [address, i18n.resolvedLanguage]);

  const onSubmit = handleSubmit((values) =>
    save.mutate(
      { id: address?.id, address: toInput(values) },
      { onSuccess: onDone, onError: (error) => applyServerFieldErrors(error, setError, FIELDS) },
    ),
  );

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField
          label={t('auth.fields.firstName')}
          autoComplete="given-name"
          error={errors.firstName?.message}
          {...register('firstName')}
        />
        <TextField
          label={t('auth.fields.lastName')}
          autoComplete="family-name"
          error={errors.lastName?.message}
          {...register('lastName')}
        />
      </div>
      <TextField
        label={t('address.form.company')}
        autoComplete="organization"
        error={errors.company?.message}
        {...register('company')}
      />
      <div className="grid grid-cols-[1fr_6.5rem] gap-5">
        <TextField
          label={t('address.form.street')}
          autoComplete="address-line1"
          error={errors.street?.message}
          {...register('street')}
        />
        <TextField
          label={t('address.form.houseNumber')}
          error={errors.houseNumber?.message}
          {...register('houseNumber')}
        />
      </div>
      <TextField
        label={t('address.form.addressLine2')}
        autoComplete="address-line2"
        error={errors.addressLine2?.message}
        {...register('addressLine2')}
      />
      <div className="grid grid-cols-[8rem_1fr] gap-5">
        <TextField
          label={t('address.form.postalCode')}
          autoComplete="postal-code"
          error={errors.postalCode?.message}
          {...register('postalCode')}
        />
        <TextField
          label={t('address.form.city')}
          autoComplete="address-level2"
          error={errors.city?.message}
          {...register('city')}
        />
      </div>
      <SelectField
        label={t('address.form.country')}
        autoComplete="country"
        error={errors.countryCode?.message}
        {...register('countryCode')}
      >
        {countries.map((country) => (
          <option key={country.code} value={country.code}>
            {country.name}
          </option>
        ))}
      </SelectField>
      <TextField
        label={t('address.form.phone')}
        type="tel"
        autoComplete="tel"
        hint={t('account.profile.phoneHint')}
        error={errors.phone?.message}
        {...register('phone')}
      />
      <TextField
        label={t('address.form.label')}
        placeholder={t('address.form.labelPlaceholder')}
        error={errors.label?.message}
        {...register('label')}
      />
      <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onDone}>
          {t('address.cancel')}
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? t('address.form.saving') : t('address.form.save')}
        </Button>
      </div>
    </form>
  );
}
