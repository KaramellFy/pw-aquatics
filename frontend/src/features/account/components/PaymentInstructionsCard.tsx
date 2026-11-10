import { Check, Copy } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { formatInstantDate } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import type { PaymentInstructions } from '../api/orders';

interface PaymentInstructionsCardProps {
  instructions: PaymentInstructions;
  /** Deadline for the payment, if the order has one. */
  dueAt: string | null;
  className?: string;
}

/** The shop's bank details for an order that is waiting for a transfer. */
export function PaymentInstructionsCard({ instructions, dueAt, className }: PaymentInstructionsCardProps) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <section
      aria-labelledby="payment-instructions"
      className={cn('rounded-card border border-brand-100 bg-brand-50 p-6 sm:p-8', className)}
    >
      <h2 id="payment-instructions" className="text-title">
        {t('orders.payment.title')}
      </h2>
      {dueAt && (
        <p className="mt-3 text-ink-soft">
          {t('orders.payment.lead', { date: formatInstantDate(dueAt, locale) })}
        </p>
      )}
      <dl className="mt-6 divide-y divide-brand-200 border-y border-brand-200">
        <Row label={t('orders.payment.accountHolder')} value={instructions.accountHolder} />
        <Row label={t('orders.payment.iban')} value={instructions.iban} copyable mono />
        {instructions.bic && <Row label={t('orders.payment.bic')} value={instructions.bic} mono />}
        {instructions.bankName && <Row label={t('orders.payment.bank')} value={instructions.bankName} />}
        <Row label={t('orders.payment.amount')} value={formatMoney(instructions.amount, locale)} />
        <Row label={t('orders.payment.reference')} value={instructions.reference} copyable mono />
      </dl>
      <p className="mt-4 text-sm text-ink-soft">{t('orders.payment.referenceHint')}</p>
      {dueAt && <p className="mt-2 text-sm text-muted">{t('orders.payment.expiry')}</p>}
    </section>
  );
}

function Row({
  label,
  value,
  copyable = false,
  mono = false,
}: {
  label: string;
  value: string;
  copyable?: boolean;
  mono?: boolean;
}) {
  return (
    <div className="flex min-h-13 flex-wrap items-center justify-between gap-x-4 gap-y-1 py-2">
      <dt className="text-sm text-ink-soft">{label}</dt>
      <dd className="flex items-center gap-2">
        <span className={cn('text-ink', mono && 'tracking-wide tabular-nums')}>{value}</span>
        {copyable && <CopyButton label={label} value={value} />}
      </dd>
    </div>
  );
}

function CopyButton({ label, value }: { label: string; value: string }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  // The clipboard needs a secure context; without it the button is simply not offered.
  if (!navigator.clipboard) return null;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Permission denied: the value stays selectable as text.
    }
  };
  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={t('orders.payment.copyLabel', { field: label })}
      onClick={() => void copy()}
    >
      {copied ? <Check aria-hidden className="size-4" /> : <Copy aria-hidden className="size-4" />}
      <span role="status" className="sr-only">
        {copied ? t('orders.payment.copied') : ''}
      </span>
    </Button>
  );
}
