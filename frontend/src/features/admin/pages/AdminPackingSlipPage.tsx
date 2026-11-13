import { ArrowLeft, Printer } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { Logo } from '@/components/layout/Logo';
import { Button } from '@/components/ui/Button';
import { AddressBlock } from '@/features/account';
import { formatCalendarDate, formatInstantDate } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useAdminOrder } from '../api/orders';
import { LoadState } from '../components/LoadState';

/**
 * A packing slip to print and put in the parcel: what is in it and where it goes, without
 * prices. Opened from the order page; printing hides the buttons (a PDF follows with invoices).
 */
export function AdminPackingSlipPage() {
  const { orderNumber = '' } = useParams();
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const order = useAdminOrder(orderNumber);
  const detail = order.data?.order;

  return (
    <div className="min-h-dvh bg-mist print:bg-white">
      <PageMeta title={t('admin.packingSlip.metaTitle', { number: orderNumber })} noIndex />
      <div className="mx-auto max-w-3xl px-5 py-6 sm:px-8 print:max-w-none print:p-0">
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3 print:hidden">
          <Link
            to={`/admin/orders/${orderNumber}`}
            className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
          >
            <ArrowLeft aria-hidden className="size-4" />
            {t('admin.packingSlip.back')}
          </Link>
          <Button onClick={() => window.print()} disabled={!detail}>
            <Printer aria-hidden className="size-4" />
            {t('admin.packingSlip.print')}
          </Button>
        </div>

        <LoadState
          isPending={order.isPending}
          isError={order.isError}
          hasData={Boolean(detail)}
          onRetry={() => void order.refetch()}
        />

        {detail && (
          <main className="rounded-card border border-line bg-surface p-6 sm:p-10 print:rounded-none print:border-0 print:p-0">
            <header className="flex flex-wrap items-start justify-between gap-6 border-b border-line pb-6">
              <Logo />
              <div className="text-right">
                <h1 className="text-title">{t('admin.packingSlip.title')}</h1>
                <p className="mt-1 text-ink tabular-nums">{detail.orderNumber}</p>
                <p className="text-sm text-ink-soft">
                  {t('admin.packingSlip.ordered', { date: formatInstantDate(detail.placedAt, locale) })}
                </p>
              </div>
            </header>

            <div className="grid gap-8 border-b border-line py-6 sm:grid-cols-2">
              <section>
                <h2 className="text-xs font-medium tracking-wide text-muted uppercase">
                  {t('orders.detail.shippingAddress')}
                </h2>
                <AddressBlock address={detail.shippingAddress} className="mt-2 text-ink" />
              </section>
              <section>
                <h2 className="text-xs font-medium tracking-wide text-muted uppercase">
                  {t('orders.detail.shipping')}
                </h2>
                <p className="mt-2 text-ink">{detail.shippingMethodName}</p>
                {detail.plannedDispatchDate && (
                  <p className="text-sm text-ink-soft">
                    {t('admin.packingSlip.dispatch', {
                      date: formatCalendarDate(detail.plannedDispatchDate, locale, 'full'),
                    })}
                  </p>
                )}
                {detail.shipments.map((shipment) => (
                  <p key={shipment.id} className="text-sm text-ink-soft tabular-nums">
                    {shipment.carrier} {shipment.trackingNumber}
                  </p>
                ))}
              </section>
            </div>

            <table className="mt-6 w-full text-left text-[0.9375rem]">
              <caption className="sr-only">{t('orders.detail.items')}</caption>
              <thead className="border-b border-line text-xs tracking-wide text-muted uppercase">
                <tr>
                  <th scope="col" className="w-12 py-2 pr-3">
                    <span className="sr-only">{t('admin.packingSlip.packed')}</span>
                  </th>
                  <th scope="col" className="py-2 pr-3 text-right">
                    {t('admin.packingSlip.quantity')}
                  </th>
                  <th scope="col" className="py-2 pr-3">
                    {t('admin.packingSlip.item')}
                  </th>
                  <th scope="col" className="py-2">
                    {t('admin.packingSlip.sku')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {detail.items.map((item) => (
                  <tr key={item.sku}>
                    <td className="py-3 pr-3 align-top">
                      <span aria-hidden className="block size-5 rounded border-2 border-line-strong" />
                    </td>
                    <td className="py-3 pr-3 text-right align-top font-medium tabular-nums">
                      {item.quantity} ×
                    </td>
                    <td className="py-3 pr-3 align-top">
                      <p className="font-medium text-ink">{item.productName}</p>
                      <p className="text-sm text-ink-soft">
                        {[item.variantName, item.gradeName].filter(Boolean).join(' · ')}
                      </p>
                      {item.speciesName && (
                        <p className="scientific text-sm text-muted">{item.speciesName}</p>
                      )}
                    </td>
                    <td className="py-3 align-top text-sm text-ink-soft tabular-nums">{item.sku}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {detail.customerNote && (
              <section className="mt-6 rounded-xl border border-line p-4">
                <h2 className="text-xs font-medium tracking-wide text-muted uppercase">
                  {t('admin.orders.customerNote')}
                </h2>
                <p className="mt-1 whitespace-pre-line text-ink">{detail.customerNote}</p>
              </section>
            )}

            {detail.containsLiveAnimals && (
              <p className="mt-6 border-t border-line pt-6 text-sm text-ink-soft">
                {t('admin.packingSlip.liveAnimals')}
              </p>
            )}
            <p className="mt-6 text-sm text-ink-soft">{t('admin.packingSlip.thanks')}</p>
          </main>
        )}
      </div>
    </div>
  );
}
