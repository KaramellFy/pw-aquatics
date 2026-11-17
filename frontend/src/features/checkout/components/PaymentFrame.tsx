import { LockKeyhole } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import type { Money } from '@/lib/api/types';
import { formatLocalTime } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';

interface PaymentFrameProps {
  orderNumber: string;
  /** The order and amount to pay, once the server has sent them. */
  summary?: ReactNode;
  /** How the customer's payment details are protected (provider-specific). */
  secure?: string;
  children: ReactNode;
}

/** The payment page around the provider's form: heading, what is being paid, and a way back. */
export function PaymentFrame({ orderNumber, summary, secure, children }: PaymentFrameProps) {
  const { t } = useTranslation();
  return (
    <>
      <PageMeta title={t('checkout.pay.metaTitle')} noIndex />
      <Container className="max-w-2xl pt-12 pb-24 sm:pt-16">
        <p className="eyebrow text-brand-700">{t('checkout.pay.eyebrow')}</p>
        <h1 className="mt-3 text-headline">{t('checkout.pay.title')}</h1>
        {summary}
        <div className="mt-8">{children}</div>
        {secure && (
          <p className="mt-8 flex gap-3 text-sm text-ink-soft">
            <LockKeyhole aria-hidden className="mt-0.5 size-4 shrink-0 text-brand-700" />
            <span>{secure}</span>
          </p>
        )}
        <p className="mt-3 text-sm text-ink-soft">
          {t('checkout.pay.later')}{' '}
          <ButtonLink to={`/account/orders/${orderNumber}`} variant="link" className="text-sm">
            {t('checkout.pay.viewOrder')}
          </ButtonLink>
        </p>
      </Container>
    </>
  );
}

/** The order number, the amount from the API and until when the animals are reserved. */
export function PaymentSummary({
  orderNumber,
  amount,
  paymentDueAt,
}: {
  orderNumber: string;
  amount: Money;
  paymentDueAt?: string | null;
}) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <div className="mt-4 space-y-1 text-lg text-ink-soft">
      <p>
        {t('checkout.pay.order', { number: orderNumber })} ·{' '}
        <strong className="font-medium text-ink tabular-nums">{formatMoney(amount, locale)}</strong>
      </p>
      {paymentDueAt && (
        <p className="text-base">
          {t('checkout.pay.deadline', { time: formatLocalTime(paymentDueAt, locale) })}
        </p>
      )}
    </div>
  );
}

/** Keeps the space of the payment form while it loads, so nothing jumps. */
export function FormPlaceholder({ label }: { label: string }) {
  return (
    <div className="flex min-h-64 items-center justify-center rounded-card border border-line bg-mist">
      <p role="status" className="text-muted">
        {label}
      </p>
    </div>
  );
}
