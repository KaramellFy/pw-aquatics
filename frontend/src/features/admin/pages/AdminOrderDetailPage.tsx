import { ExternalLink, Printer } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { TextAreaField } from '@/components/ui/Field';
import { Price } from '@/components/ui/Price';
import { AddressBlock, OrderStatusBadge, OrderTotalsList } from '@/features/account';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatCalendarDate, formatInstantDate, formatInstantDateTime } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';
import {
  useAddOrderNote,
  useAdminOrder,
  useRemoveShipment,
  type AdminOrder,
  type Shipment,
} from '../api/orders';
import { AdminCard } from '../components/AdminCard';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { LoadState } from '../components/LoadState';
import {
  CancelOrderDialog,
  MarkPaidDialog,
  ShipmentDialog,
  StatusDialog,
} from '../components/orders/OrderActionDialogs';
import { Pill } from '../components/StatusBadge';

type DialogKind = 'markPaid' | 'processing' | 'delivered' | 'ship' | 'correct' | 'cancel';

/** One order for the shop owner: what to do next, parcels, notes, history and all details. */
export function AdminOrderDetailPage() {
  const { orderNumber = '' } = useParams();
  const { t } = useTranslation();
  const order = useAdminOrder(orderNumber);
  const back = { to: '/admin/orders', label: t('admin.orders.back') };

  if (order.isError && !order.data) {
    const notFound =
      order.error instanceof ApiError && (order.error.status === 404 || order.error.status === 400);
    if (notFound) {
      return (
        <>
          <AdminPageHeader title={t('admin.orders.notFound')} back={back} />
          <p className="text-ink-soft">{t('admin.orders.notFoundText')}</p>
        </>
      );
    }
  }
  return (
    <>
      <PageMeta title={t('admin.orders.metaTitle', { number: orderNumber })} noIndex />
      <LoadState
        isPending={order.isPending}
        isError={order.isError}
        hasData={Boolean(order.data)}
        onRetry={() => void order.refetch()}
      />
      {order.data && <OrderDetail order={order.data} back={back} />}
    </>
  );
}

function OrderDetail({ order, back }: { order: AdminOrder; back: { to: string; label: string } }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const detail = order.order;
  const [dialog, setDialog] = useState<DialogKind | null>(null);
  const [correcting, setCorrecting] = useState<Shipment | undefined>(undefined);
  // A fresh form every time a dialog opens; closed dialogs stay mounted for their exit animation.
  const [dialogKey, setDialogKey] = useState(0);
  const open = (next: DialogKind, shipment?: Shipment) => {
    setDialogKey((key) => key + 1);
    if (shipment) setCorrecting(shipment);
    setDialog(next);
  };
  const close = (isOpen: boolean) => !isOpen && setDialog(null);
  const customerName = `${detail.billingAddress.firstName} ${detail.billingAddress.lastName}`;
  const { actions } = order;
  const anyAction =
    actions.markPaid || actions.startProcessing || actions.ship || actions.markDelivered || actions.cancel;

  return (
    <>
      <AdminPageHeader
        title={t('orders.number', { number: detail.orderNumber })}
        lead={t('admin.orders.placedBy', {
          date: formatInstantDateTime(detail.placedAt, locale),
          name: customerName,
        })}
        back={back}
        actions={
          <ButtonLink variant="secondary" to={`/admin/orders/${detail.orderNumber}/packing-slip`}>
            <Printer aria-hidden className="size-4" />
            {t('admin.orders.packingSlip')}
          </ButtonLink>
        }
      />

      <div className="mb-6 flex flex-wrap items-center gap-2">
        <OrderStatusBadge status={detail.status} />
        <Pill tone={detail.paymentStatus === 'PAID' ? 'ok' : 'neutral'}>
          {t(`admin.orders.paymentStatus.${detail.paymentStatus}`)}
        </Pill>
        {detail.containsLiveAnimals && <Pill>{t('admin.orders.live')}</Pill>}
      </div>

      <AdminCard title={t('admin.orders.nextStep')} id="next-step" className="mb-6">
        {anyAction ? (
          <div className="flex flex-wrap gap-3">
            {actions.markPaid && (
              <Button onClick={() => open('markPaid')}>{t('admin.orders.actions.markPaid')}</Button>
            )}
            {actions.startProcessing && (
              <Button variant={actions.ship ? 'secondary' : 'primary'} onClick={() => open('processing')}>
                {t('admin.orders.actions.startProcessing')}
              </Button>
            )}
            {actions.ship && (
              <Button
                variant={detail.status === 'SHIPPED' ? 'secondary' : 'primary'}
                onClick={() => open('ship')}
              >
                {t(
                  detail.status === 'SHIPPED'
                    ? 'admin.orders.actions.addParcel'
                    : 'admin.orders.actions.ship',
                )}
              </Button>
            )}
            {actions.markDelivered && (
              <Button onClick={() => open('delivered')}>{t('admin.orders.actions.markDelivered')}</Button>
            )}
            {actions.cancel && (
              <Button variant="ghost" className="text-danger" onClick={() => open('cancel')}>
                {t('admin.orders.actions.cancel')}
              </Button>
            )}
          </div>
        ) : (
          <p className="text-ink-soft">
            {t(detail.status === 'CANCELLED' ? 'admin.orders.doneCancelled' : 'admin.orders.doneDelivered')}
          </p>
        )}
        {detail.status === 'PENDING_PAYMENT' && detail.paymentDueAt && (
          <p className="mt-4 text-sm text-ink-soft">
            {t('admin.orders.paymentDue', { date: formatInstantDateTime(detail.paymentDueAt, locale) })}
          </p>
        )}
      </AdminCard>

      <div className="grid gap-6 lg:grid-cols-12">
        <div className="space-y-6 lg:col-span-7">
          <AdminCard title={t('orders.detail.items')} id="items">
            <ul className="divide-y divide-line border-y border-line">
              {detail.items.map((item) => (
                <li key={item.sku} className="flex justify-between gap-4 py-3">
                  <div className="min-w-0">
                    <p className="font-medium text-ink">
                      <span className="tabular-nums">{item.quantity} ×</span> {item.productName}
                    </p>
                    <p className="text-sm text-ink-soft">
                      {[item.variantName, item.gradeName, item.speciesName].filter(Boolean).join(' · ')}
                    </p>
                    <p className="text-xs text-muted">
                      {t('admin.orders.sku', { sku: item.sku })} ·{' '}
                      {t('orders.detail.each', { price: formatMoney(item.unitPrice, locale) })}
                    </p>
                  </div>
                  <Price value={item.lineTotal} className="shrink-0" />
                </li>
              ))}
            </ul>
            <OrderTotalsList totals={detail.totals} className="mt-5" />
          </AdminCard>

          <ShipmentsCard order={order} onCorrect={(shipment) => open('correct', shipment)} />
          <NotesCard order={order} />
          <HistoryCard order={order} />
        </div>

        <div className="space-y-6 lg:col-span-5">
          <AdminCard title={t('admin.orders.customer')} id="customer">
            <p className="text-ink">{customerName}</p>
            <a
              href={`mailto:${order.customerEmail}?subject=${encodeURIComponent(t('orders.number', { number: detail.orderNumber }))}`}
              className="break-all text-brand-700 underline-offset-4 hover:underline"
            >
              {order.customerEmail}
            </a>
            <p className="mt-1 text-sm text-muted">
              {t('admin.orders.emailLanguage', {
                language: t(`common.languages.${order.customerLocale === 'de' ? 'de' : 'en'}`),
              })}
            </p>
            {order.customerId && (
              <Link
                to={`/admin/customers/${order.customerId}`}
                className="mt-3 inline-flex min-h-11 items-center text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
              >
                {t('admin.orders.viewCustomer')}
              </Link>
            )}
            {detail.customerNote && (
              <div className="mt-4 rounded-xl bg-mist p-4">
                <p className="text-xs font-medium tracking-wide text-muted uppercase">
                  {t('admin.orders.customerNote')}
                </p>
                <p className="mt-1 whitespace-pre-line text-ink">{detail.customerNote}</p>
              </div>
            )}
          </AdminCard>

          <AdminCard title={t('orders.detail.shipping')} id="shipping">
            <p className="text-ink">{detail.shippingMethodName}</p>
            {detail.plannedDispatchDate && detail.status !== 'CANCELLED' && (
              <p className="mt-1 text-sm text-ink-soft">
                {t('orders.detail.earliestDispatch', {
                  date: formatCalendarDate(detail.plannedDispatchDate, locale, 'full'),
                })}
              </p>
            )}
            <h3 className="mt-5 text-sm font-medium text-ink-soft">{t('orders.detail.shippingAddress')}</h3>
            <AddressBlock address={detail.shippingAddress} className="mt-2" />
            <h3 className="mt-5 text-sm font-medium text-ink-soft">{t('orders.detail.billingAddress')}</h3>
            <AddressBlock address={detail.billingAddress} className="mt-2" />
          </AdminCard>

          <PaymentCard order={order} />
        </div>
      </div>

      <MarkPaidDialog
        key={`paid-${dialogKey}`}
        order={order}
        open={dialog === 'markPaid'}
        onOpenChange={close}
      />
      <StatusDialog
        key={`processing-${dialogKey}`}
        order={order}
        status="PROCESSING"
        open={dialog === 'processing'}
        onOpenChange={close}
      />
      <StatusDialog
        key={`delivered-${dialogKey}`}
        order={order}
        status="DELIVERED"
        open={dialog === 'delivered'}
        onOpenChange={close}
      />
      <ShipmentDialog key={`ship-${dialogKey}`} order={order} open={dialog === 'ship'} onOpenChange={close} />
      {correcting && (
        <ShipmentDialog
          key={`correct-${dialogKey}`}
          order={order}
          shipment={correcting}
          open={dialog === 'correct'}
          onOpenChange={close}
        />
      )}
      <CancelOrderDialog
        key={`cancel-${dialogKey}`}
        order={order}
        open={dialog === 'cancel'}
        onOpenChange={close}
      />
    </>
  );
}

function ShipmentsCard({ order, onCorrect }: { order: AdminOrder; onCorrect: (shipment: Shipment) => void }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const remove = useRemoveShipment(order.order.orderNumber);
  const errorMessage = useApiErrorMessage();
  const [removing, setRemoving] = useState<Shipment | null>(null);
  const shipments = order.order.shipments;
  return (
    <AdminCard title={t('admin.orders.parcels')} id="parcels">
      {remove.isError && (
        <Alert tone="error" className="mb-4">
          {errorMessage(remove.error)}
        </Alert>
      )}
      {shipments.length === 0 ? (
        <p className="text-ink-soft">{t('admin.orders.noParcels')}</p>
      ) : (
        <ul className="space-y-3">
          {shipments.map((shipment) => (
            <li key={shipment.id} className="rounded-xl border border-line p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-ink">
                    {shipment.carrier} · <span className="tabular-nums">{shipment.trackingNumber}</span>
                  </p>
                  <p className="text-sm text-ink-soft">
                    {t('orders.tracking.shippedOn', { date: formatInstantDate(shipment.shippedAt, locale) })}
                    {shipment.deliveredAt &&
                      ` · ${t('orders.tracking.deliveredOn', { date: formatInstantDate(shipment.deliveredAt, locale) })}`}
                  </p>
                  {shipment.trackingUrl && (
                    <a
                      href={shipment.trackingUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="mt-1 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
                    >
                      {t('orders.tracking.track')}
                      <ExternalLink aria-hidden className="size-4" />
                      <span className="sr-only">{t('common.opensInNewTab')}</span>
                    </a>
                  )}
                </div>
                <div className="flex gap-1">
                  {order.order.status !== 'CANCELLED' && (
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={t('admin.orders.correctParcel', { number: shipment.trackingNumber })}
                      onClick={() => onCorrect(shipment)}
                    >
                      {t('admin.common.edit')}
                    </Button>
                  )}
                  {order.actions.removeShipment && (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-danger"
                      aria-label={t('admin.orders.removeParcel', { number: shipment.trackingNumber })}
                      onClick={() => setRemoving(shipment)}
                    >
                      {t('admin.common.delete')}
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(isOpen) => !isOpen && setRemoving(null)}
        title={t('admin.orders.removeParcelTitle', { number: removing?.trackingNumber ?? '' })}
        description={t('admin.orders.removeParcelText')}
        confirmLabel={t('admin.common.delete')}
        danger
        pending={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing.id, { onSettled: () => setRemoving(null) })}
      />
    </AdminCard>
  );
}

function NotesCard({ order }: { order: AdminOrder }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const addNote = useAddOrderNote(order.order.orderNumber);
  const errorMessage = useApiErrorMessage();
  const [body, setBody] = useState('');
  const [empty, setEmpty] = useState(false);
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (addNote.isPending) return;
    if (!body.trim()) {
      setEmpty(true);
      return;
    }
    setEmpty(false);
    addNote.mutate(body.trim(), { onSuccess: () => setBody('') });
  };
  return (
    <AdminCard title={t('admin.orders.notes')} id="notes">
      <p className="-mt-3 mb-4 text-sm text-muted">{t('admin.orders.notesHint')}</p>
      {order.notes.length > 0 && (
        <ul className="mb-5 space-y-3">
          {order.notes.map((note) => (
            <li key={note.id} className="rounded-xl bg-mist p-4">
              <p className="whitespace-pre-line text-ink">{note.body}</p>
              <p className="mt-2 text-xs text-muted">
                {[note.author, formatInstantDateTime(note.at, locale)].filter(Boolean).join(' · ')}
              </p>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={onSubmit} noValidate className="space-y-3">
        {addNote.isError && <Alert tone="error">{errorMessage(addNote.error)}</Alert>}
        <TextAreaField
          label={t('admin.orders.addNote')}
          rows={3}
          maxLength={2000}
          value={body}
          onChange={(event) => setBody(event.target.value)}
          error={empty ? t('admin.common.required') : undefined}
        />
        <Button
          type="submit"
          variant="secondary"
          aria-disabled={addNote.isPending || undefined}
          aria-busy={addNote.isPending || undefined}
        >
          {addNote.isPending ? t('admin.common.saving') : t('admin.orders.saveNote')}
        </Button>
      </form>
    </AdminCard>
  );
}

function HistoryCard({ order }: { order: AdminOrder }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <AdminCard title={t('admin.orders.history')} id="history">
      <ol className="space-y-4">
        {order.history.map((entry) => (
          <li key={`${entry.to}-${entry.at}`} className="border-l-2 border-line pl-4">
            <p className="font-medium text-ink">{t(`orders.status.${entry.to}`)}</p>
            <p className="text-sm text-muted">
              {[formatInstantDateTime(entry.at, locale), entry.changedBy ?? t('admin.orders.bySystem')].join(
                ' · ',
              )}
            </p>
            {entry.note && (
              <p className="mt-1 text-sm text-ink-soft">
                <span className="font-medium">{t('admin.orders.internal')}:</span> {entry.note}
              </p>
            )}
            {entry.customerMessage && (
              <p className="mt-1 text-sm text-ink-soft">
                <span className="font-medium">{t('admin.orders.toCustomer')}:</span> {entry.customerMessage}
              </p>
            )}
          </li>
        ))}
      </ol>
    </AdminCard>
  );
}

function PaymentCard({ order }: { order: AdminOrder }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const detail = order.order;
  return (
    <AdminCard title={t('orders.detail.payment')} id="payment">
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-[0.9375rem]">
        <dt className="text-muted">{t('admin.orders.method')}</dt>
        <dd className="text-ink">{t(`admin.orders.paymentMethod.${detail.paymentMethod}`)}</dd>
        <dt className="text-muted">{t('admin.orders.columns.status')}</dt>
        <dd className="text-ink">{t(`admin.orders.paymentStatus.${detail.paymentStatus}`)}</dd>
        <dt className="text-muted">{t('admin.orders.amount')}</dt>
        <dd className="text-ink">{formatMoney(detail.totals.total, locale)}</dd>
        {order.paidAt && (
          <>
            <dt className="text-muted">{t('admin.orders.paidAt')}</dt>
            <dd className="text-ink">{formatInstantDateTime(order.paidAt, locale)}</dd>
          </>
        )}
        {order.payment && (
          <>
            <dt className="text-muted">{t('admin.orders.stripeStatus')}</dt>
            <dd className="text-ink">
              {t(`admin.orders.stripeStatuses.${order.payment.status}`)}
              {order.payment.methodDetail && ` · ${order.payment.methodDetail}`}
            </dd>
            {order.payment.failureCode && (
              <>
                <dt className="text-muted">{t('admin.orders.declined')}</dt>
                <dd className="text-ink">{order.payment.failureCode}</dd>
              </>
            )}
            {order.payment.refunded.amount > 0 && (
              <>
                <dt className="text-muted">{t('admin.orders.refunded')}</dt>
                <dd className="text-ink">{formatMoney(order.payment.refunded, locale)}</dd>
              </>
            )}
          </>
        )}
        {order.cancelledAt && (
          <>
            <dt className="text-muted">{t('admin.orders.cancelledAt')}</dt>
            <dd className="text-ink">
              {formatInstantDateTime(order.cancelledAt, locale)}
              {order.cancellationReason && (
                <span className="block text-sm text-ink-soft">{order.cancellationReason}</span>
              )}
            </dd>
          </>
        )}
      </dl>
      {order.payment && (
        <a
          href={order.payment.dashboardUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-4 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
        >
          {t('admin.orders.openInStripe')}
          <ExternalLink aria-hidden className="size-4" />
          <span className="sr-only">{t('common.opensInNewTab')}</span>
        </a>
      )}
      {order.paidAt && detail.status === 'CANCELLED' && detail.paymentStatus === 'PAID' && (
        <p className="mt-4 text-sm text-ink-soft">{t('admin.orders.refundReminder')}</p>
      )}
    </AdminCard>
  );
}
