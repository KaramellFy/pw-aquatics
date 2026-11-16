import { TicketPercent, X } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { couponBenefit } from '@/lib/format/coupon';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { useApplyCoupon, useRemoveCoupon, type Cart, type CouponState } from '../api/cart';

/**
 * Coupon code entry in the cart. Whether a code is valid, whether it applies and what it saves
 * are decided by the server; this only sends the code and shows the answer.
 */
export function CouponForm({ cart }: { cart: Cart }) {
  const { t } = useTranslation();
  const errorMessage = useApiErrorMessage();
  const apply = useApplyCoupon();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string>();

  if (cart.coupon) {
    return <AppliedCoupon coupon={cart.coupon} />;
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (apply.isPending) return;
    if (!code.trim()) {
      setError(t('cart.coupon.required'));
      return;
    }
    setError(undefined);
    apply.mutate(code.trim(), {
      onSuccess: () => setCode(''),
      onError: (failure) => setError(errorMessage(failure)),
    });
  };

  return (
    <form onSubmit={submit} noValidate className="flex items-start gap-3">
      <TextField
        label={t('cart.coupon.label')}
        className="min-w-0 flex-1"
        value={code}
        onChange={(event) => setCode(event.target.value)}
        error={error}
        maxLength={40}
        autoComplete="off"
        autoCapitalize="characters"
        spellCheck={false}
      />
      <Button
        type="submit"
        variant="secondary"
        className="mt-7 h-12 shrink-0"
        aria-disabled={apply.isPending || undefined}
        aria-busy={apply.isPending || undefined}
      >
        {t('cart.coupon.apply')}
      </Button>
    </form>
  );
}

function AppliedCoupon({ coupon }: { coupon: CouponState }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const remove = useRemoveCoupon();
  return (
    <div className="rounded-xl border border-brand-200 bg-surface p-4">
      <div className="flex items-start gap-3">
        <TicketPercent aria-hidden className="mt-0.5 size-5 shrink-0 text-brand-700" />
        <div className="min-w-0 flex-1">
          <p className="font-medium text-ink">
            <span className="sr-only">{t('cart.coupon.label')}: </span>
            {coupon.code}
          </p>
          <p className="text-sm text-ink-soft">{couponBenefit(coupon, t, locale)}</p>
          <p role="status" className={coupon.applied ? 'text-sm text-success' : 'text-sm text-warning'}>
            {coupon.applied
              ? coupon.type === 'FREE_SHIPPING'
                ? t('cart.coupon.freeShippingAtCheckout')
                : t('cart.coupon.saving', { amount: formatMoney(coupon.saving, locale) })
              : t(`cart.coupon.problems.${coupon.problem ?? 'COUPON_INVALID'}`, {
                  amount: coupon.minOrder ? formatMoney(coupon.minOrder, locale) : '',
                  defaultValue: t('cart.coupon.problems.COUPON_INVALID'),
                })}
          </p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          className="-mt-1 -mr-2 shrink-0"
          aria-label={t('cart.coupon.remove', { code: coupon.code })}
          aria-disabled={remove.isPending || undefined}
          aria-busy={remove.isPending || undefined}
          onClick={() => !remove.isPending && remove.mutate()}
        >
          <X aria-hidden className="size-4" />
        </Button>
      </div>
    </div>
  );
}
