import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { CheckboxField, SelectField, TextField } from '@/components/ui/Field';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { moneyInputValue, parseMoneyInput } from '@/lib/format/money';
import { useSaveCoupon, type AdminCoupon, type CouponType } from '../api/coupons';

const TYPES: CouponType[] = ['PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING'];
const CODE_FORMAT = /^[A-Z0-9][A-Z0-9_-]{2,31}$/;

interface CouponForm {
  code: string;
  description: string;
  type: CouponType;
  /** Percent ("12,5") or euros ("5,00"), depending on the type. */
  value: string;
  maxDiscount: string;
  minOrder: string;
  startsOn: string;
  endsOn: string;
  usageLimitTotal: string;
  usageLimitPerCustomer: string;
  active: boolean;
}

function initialForm(coupon: AdminCoupon | null): CouponForm {
  return {
    code: coupon?.code ?? '',
    description: coupon?.description ?? '',
    type: coupon?.type ?? 'PERCENTAGE',
    // Basis points and cents both have two decimals: 1250 → "12.50", 500 → "5.00".
    value:
      coupon?.percentage != null
        ? moneyInputValue(coupon.percentage)
        : coupon?.amount
          ? moneyInputValue(coupon.amount.amount)
          : '',
    maxDiscount: coupon?.maxDiscount ? moneyInputValue(coupon.maxDiscount.amount) : '',
    minOrder: coupon?.minOrder ? moneyInputValue(coupon.minOrder.amount) : '',
    startsOn: coupon?.startsOn ?? '',
    endsOn: coupon?.endsOn ?? '',
    usageLimitTotal: coupon?.usageLimitTotal?.toString() ?? '',
    usageLimitPerCustomer: coupon?.usageLimitPerCustomer?.toString() ?? '',
    active: coupon?.active ?? true,
  };
}

/**
 * Creates a coupon (`coupon` is "new") or edits one. Only collects and parses input: whether
 * the terms fit together is checked by the server too, and the code cannot change once created.
 */
export function CouponDialog({
  coupon,
  onClose,
  onSaved,
}: {
  coupon: AdminCoupon | 'new' | null;
  onClose: () => void;
  onSaved?: (coupon: AdminCoupon) => void;
}) {
  const { t } = useTranslation();
  const save = useSaveCoupon();
  const errorMessage = useApiErrorMessage();
  const existing = coupon && coupon !== 'new' ? coupon : null;
  const [form, setForm] = useState<CouponForm>(() => initialForm(existing));
  const [errors, setErrors] = useState<Partial<Record<keyof CouponForm, string>>>({});
  const set = <K extends keyof CouponForm>(key: K, value: CouponForm[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (save.isPending) return;
    const found: Partial<Record<keyof CouponForm, string>> = {};
    const optionalMoney = (text: string) => (text.trim() ? parseMoneyInput(text) : undefined);
    const whole = (text: string) =>
      text.trim() === '' ? undefined : /^\d{1,7}$/.test(text.trim()) ? Number(text) : NaN;
    // "12,5" % → 1250 basis points; "5" € → 500 cents (both in hundredths).
    const value = form.type === 'FREE_SHIPPING' ? 0 : parseMoneyInput(form.value);
    const maxDiscount = form.type === 'PERCENTAGE' ? optionalMoney(form.maxDiscount) : undefined;
    const minOrder = optionalMoney(form.minOrder);
    const total = whole(form.usageLimitTotal);
    const perCustomer = whole(form.usageLimitPerCustomer);
    const code = form.code.trim().toUpperCase();

    if (!existing && !CODE_FORMAT.test(code)) found.code = t('admin.coupons.form.codeInvalid');
    if (value === null || (form.type !== 'FREE_SHIPPING' && value === 0)) {
      found.value = t('admin.common.numberInvalid');
    } else if (form.type === 'PERCENTAGE' && value > 10_000) {
      found.value = t('admin.coupons.form.percentageTooHigh');
    }
    if (maxDiscount === null || maxDiscount === 0) found.maxDiscount = t('admin.common.numberInvalid');
    if (minOrder === null) found.minOrder = t('admin.common.numberInvalid');
    if (Number.isNaN(total) || total === 0) found.usageLimitTotal = t('admin.common.wholeNumberInvalid');
    if (Number.isNaN(perCustomer) || perCustomer === 0) {
      found.usageLimitPerCustomer = t('admin.common.wholeNumberInvalid');
    }
    setErrors(found);
    if (Object.keys(found).length > 0 || value === null) return;
    save.mutate(
      {
        id: existing?.id,
        input: {
          code: existing ? undefined : code,
          description: form.description.trim() || undefined,
          type: form.type,
          value,
          minOrderCents: minOrder ?? 0,
          maxDiscountCents: maxDiscount ?? undefined,
          startsOn: form.startsOn || undefined,
          endsOn: form.endsOn || undefined,
          usageLimitTotal: total,
          usageLimitPerCustomer: perCustomer,
          active: form.active,
          version: existing?.version ?? 0,
        },
      },
      {
        onSuccess: (saved) => {
          onSaved?.(saved);
          onClose();
        },
      },
    );
  };

  return (
    <Dialog
      open={coupon !== null}
      onOpenChange={(isOpen) => !isOpen && onClose()}
      title={
        existing
          ? t('admin.coupons.form.editTitle', { code: existing.code })
          : t('admin.coupons.form.newTitle')
      }
      className="max-w-2xl"
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        <div className="grid gap-5 sm:grid-cols-2">
          {existing ? (
            <TextField
              label={t('admin.coupons.form.code')}
              value={existing.code}
              readOnly
              hint={t('admin.coupons.form.codeFixed')}
            />
          ) : (
            <TextField
              label={t('admin.coupons.form.code')}
              hint={t('admin.coupons.form.codeHint')}
              maxLength={32}
              autoCapitalize="characters"
              spellCheck={false}
              value={form.code}
              onChange={(event) => set('code', event.target.value.toUpperCase())}
              error={errors.code}
            />
          )}
          <TextField
            label={`${t('admin.coupons.form.description')} (${t('admin.common.optional')})`}
            hint={t('admin.coupons.form.descriptionHint')}
            maxLength={255}
            value={form.description}
            onChange={(event) => set('description', event.target.value)}
          />
          <SelectField
            label={t('admin.coupons.form.type')}
            value={form.type}
            onChange={(event) => set('type', event.target.value as CouponType)}
          >
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {t(`admin.coupons.types.${type}`)}
              </option>
            ))}
          </SelectField>
          {form.type !== 'FREE_SHIPPING' && (
            <TextField
              label={
                form.type === 'PERCENTAGE'
                  ? t('admin.coupons.form.percentage')
                  : t('admin.coupons.form.amount')
              }
              inputMode="decimal"
              value={form.value}
              onChange={(event) => set('value', event.target.value)}
              error={errors.value}
            />
          )}
          {form.type === 'PERCENTAGE' && (
            <TextField
              label={`${t('admin.coupons.form.maxDiscount')} (${t('admin.common.optional')})`}
              hint={t('admin.coupons.form.maxDiscountHint')}
              inputMode="decimal"
              value={form.maxDiscount}
              onChange={(event) => set('maxDiscount', event.target.value)}
              error={errors.maxDiscount}
            />
          )}
          <TextField
            label={`${t('admin.coupons.form.minOrder')} (${t('admin.common.optional')})`}
            hint={t('admin.coupons.form.minOrderHint')}
            inputMode="decimal"
            value={form.minOrder}
            onChange={(event) => set('minOrder', event.target.value)}
            error={errors.minOrder}
          />
          <TextField
            type="date"
            label={`${t('admin.coupons.form.startsOn')} (${t('admin.common.optional')})`}
            value={form.startsOn}
            onChange={(event) => set('startsOn', event.target.value)}
          />
          <TextField
            type="date"
            label={`${t('admin.coupons.form.endsOn')} (${t('admin.common.optional')})`}
            hint={t('admin.coupons.form.endsOnHint')}
            value={form.endsOn}
            onChange={(event) => set('endsOn', event.target.value)}
          />
          <TextField
            label={`${t('admin.coupons.form.usageLimitTotal')} (${t('admin.common.optional')})`}
            inputMode="numeric"
            value={form.usageLimitTotal}
            onChange={(event) => set('usageLimitTotal', event.target.value)}
            error={errors.usageLimitTotal}
          />
          <TextField
            label={`${t('admin.coupons.form.usageLimitPerCustomer')} (${t('admin.common.optional')})`}
            inputMode="numeric"
            value={form.usageLimitPerCustomer}
            onChange={(event) => set('usageLimitPerCustomer', event.target.value)}
            error={errors.usageLimitPerCustomer}
          />
        </div>
        <p className="text-sm text-ink-soft">{t('admin.coupons.form.rules')}</p>
        <CheckboxField
          label={t('admin.coupons.form.active')}
          checked={form.active}
          onChange={(event) => set('active', event.target.checked)}
        />
        <div className="flex flex-col-reverse gap-3 pt-1 sm:flex-row sm:justify-end">
          <Button variant="secondary" onClick={onClose}>
            {t('admin.common.cancel')}
          </Button>
          <Button
            type="submit"
            aria-disabled={save.isPending || undefined}
            aria-busy={save.isPending || undefined}
          >
            {save.isPending ? t('admin.common.saving') : t('admin.common.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
