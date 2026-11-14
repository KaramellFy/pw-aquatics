import { useState, type FormEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { CheckboxField, SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import {
  useAddShipment,
  useCancelByShop,
  useChangeOrderStatus,
  useCorrectShipment,
  useMarkPaid,
  type AdminOrder,
  type Shipment,
} from '../../api/orders';

/** Tracking numbers: letters, digits and spaces (the server drops the spaces). */
const TRACKING_NUMBER = /^[A-Za-z0-9 ]{4,40}$/;

interface DialogProps {
  order: AdminOrder;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Dialog with a form, the server's error and cancel / confirm buttons. */
function ActionDialog({
  open,
  onOpenChange,
  title,
  description,
  error,
  pending,
  confirmLabel,
  danger = false,
  onSubmit,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  error: unknown;
  pending: boolean;
  confirmLabel: string;
  danger?: boolean;
  onSubmit: () => void;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  const errorMessage = useApiErrorMessage();
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!pending) onSubmit();
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title} description={description}>
      <form onSubmit={submit} noValidate className="space-y-5">
        {error != null && <Alert tone="error">{errorMessage(error)}</Alert>}
        {children}
        <div className="flex flex-col-reverse gap-3 pt-1 sm:flex-row sm:justify-end">
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            {t('admin.common.cancel')}
          </Button>
          <Button
            type="submit"
            aria-disabled={pending || undefined}
            aria-busy={pending || undefined}
            className={danger ? 'bg-danger hover:bg-danger/90' : undefined}
          >
            {pending ? t('admin.common.saving') : confirmLabel}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function CustomerMessageField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { t } = useTranslation();
  return (
    <TextAreaField
      label={`${t('admin.orders.dialogs.customerMessage')} (${t('admin.common.optional')})`}
      hint={t('admin.orders.dialogs.customerMessageHint')}
      rows={3}
      maxLength={1000}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

function NotifyField({ checked, onChange }: { checked: boolean; onChange: (checked: boolean) => void }) {
  const { t } = useTranslation();
  return (
    <CheckboxField
      label={t('admin.orders.dialogs.notify')}
      checked={checked}
      onChange={(event) => onChange(event.target.checked)}
    />
  );
}

/** Records the arrival of a bank transfer. */
export function MarkPaidDialog({ order, open, onOpenChange }: DialogProps) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const markPaid = useMarkPaid(order.order.orderNumber);
  const [paidOn, setPaidOn] = useState('');
  const [note, setNote] = useState('');
  const [notify, setNotify] = useState(true);
  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('admin.orders.dialogs.markPaidTitle')}
      description={t('admin.orders.dialogs.markPaidText', {
        amount: formatMoney(order.order.totals.total, locale),
        reference: order.order.orderNumber,
      })}
      error={markPaid.error}
      pending={markPaid.isPending}
      confirmLabel={t('admin.orders.actions.markPaid')}
      onSubmit={() =>
        markPaid.mutate(
          { paidOn: paidOn || undefined, note: note.trim() || undefined, notifyCustomer: notify },
          { onSuccess: () => onOpenChange(false) },
        )
      }
    >
      <TextField
        type="date"
        label={t('admin.orders.dialogs.paidOn')}
        hint={t('admin.orders.dialogs.todayIfEmpty')}
        value={paidOn}
        onChange={(event) => setPaidOn(event.target.value)}
      />
      <TextField
        label={`${t('admin.orders.dialogs.internalNote')} (${t('admin.common.optional')})`}
        maxLength={1000}
        value={note}
        onChange={(event) => setNote(event.target.value)}
      />
      <NotifyField checked={notify} onChange={setNotify} />
    </ActionDialog>
  );
}

/** Moves the order to "being prepared" or "delivered". */
export function StatusDialog({
  order,
  status,
  open,
  onOpenChange,
}: DialogProps & { status: 'PROCESSING' | 'DELIVERED' }) {
  const { t } = useTranslation();
  const change = useChangeOrderStatus(order.order.orderNumber);
  const [message, setMessage] = useState('');
  const [note, setNote] = useState('');
  const [notify, setNotify] = useState(true);
  const key = status === 'PROCESSING' ? 'startProcessing' : 'markDelivered';
  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t(`admin.orders.dialogs.${key}Title`)}
      description={t(`admin.orders.dialogs.${key}Text`)}
      error={change.error}
      pending={change.isPending}
      confirmLabel={t(`admin.orders.actions.${key}`)}
      onSubmit={() =>
        change.mutate(
          {
            status,
            note: note.trim() || undefined,
            customerMessage: message.trim() || undefined,
            notifyCustomer: notify,
          },
          { onSuccess: () => onOpenChange(false) },
        )
      }
    >
      <CustomerMessageField value={message} onChange={setMessage} />
      <TextField
        label={`${t('admin.orders.dialogs.internalNote')} (${t('admin.common.optional')})`}
        maxLength={1000}
        value={note}
        onChange={(event) => setNote(event.target.value)}
      />
      <NotifyField checked={notify} onChange={setNotify} />
    </ActionDialog>
  );
}

/**
 * Enters a parcel (the first one marks the order as shipped and emails the tracking link) or,
 * with {@code shipment}, corrects the carrier or tracking number of one.
 */
export function ShipmentDialog({
  order,
  shipment,
  open,
  onOpenChange,
}: DialogProps & { shipment?: Shipment }) {
  const { t } = useTranslation();
  const add = useAddShipment(order.order.orderNumber);
  const correct = useCorrectShipment(order.order.orderNumber);
  const mutation = shipment ? correct : add;
  const [carrier, setCarrier] = useState(shipment?.carrier ?? order.carriers[0] ?? '');
  const [trackingNumber, setTrackingNumber] = useState(shipment?.trackingNumber ?? '');
  const [shippedOn, setShippedOn] = useState('');
  const [message, setMessage] = useState('');
  const [notify, setNotify] = useState(true);
  const [invalid, setInvalid] = useState(false);
  const firstParcel = !shipment && order.order.shipments.length === 0;

  const onSubmit = () => {
    if (!TRACKING_NUMBER.test(trackingNumber.trim())) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    const close = { onSuccess: () => onOpenChange(false) };
    if (shipment) {
      correct.mutate({ id: shipment.id, input: { carrier, trackingNumber: trackingNumber.trim() } }, close);
    } else {
      add.mutate(
        {
          carrier,
          trackingNumber: trackingNumber.trim(),
          shippedOn: shippedOn || undefined,
          customerMessage: message.trim() || undefined,
          notifyCustomer: notify,
        },
        close,
      );
    }
  };

  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t(shipment ? 'admin.orders.dialogs.correctTitle' : 'admin.orders.dialogs.shipTitle')}
      description={
        shipment
          ? t('admin.orders.dialogs.correctText')
          : t(firstParcel ? 'admin.orders.dialogs.shipText' : 'admin.orders.dialogs.shipMoreText')
      }
      error={mutation.error}
      pending={mutation.isPending}
      confirmLabel={t(shipment ? 'admin.common.save' : 'admin.orders.actions.ship')}
      onSubmit={onSubmit}
    >
      <SelectField
        label={t('admin.orders.dialogs.carrier')}
        value={carrier}
        onChange={(event) => setCarrier(event.target.value)}
      >
        {order.carriers.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </SelectField>
      <TextField
        label={t('admin.orders.dialogs.trackingNumber')}
        hint={t('admin.orders.dialogs.trackingNumberHint')}
        autoComplete="off"
        spellCheck={false}
        maxLength={40}
        value={trackingNumber}
        onChange={(event) => setTrackingNumber(event.target.value)}
        error={invalid ? t('admin.orders.dialogs.trackingNumberInvalid') : undefined}
      />
      {!shipment && (
        <>
          <TextField
            type="date"
            label={t('admin.orders.dialogs.shippedOn')}
            hint={t('admin.orders.dialogs.todayIfEmpty')}
            value={shippedOn}
            onChange={(event) => setShippedOn(event.target.value)}
          />
          <CustomerMessageField value={message} onChange={setMessage} />
          <NotifyField checked={notify} onChange={setNotify} />
        </>
      )}
    </ActionDialog>
  );
}

/** Cancels an order that has not been shipped. */
export function CancelOrderDialog({ order, open, onOpenChange }: DialogProps) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const cancel = useCancelByShop(order.order.orderNumber);
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState('');
  const [restock, setRestock] = useState(true);
  const [notify, setNotify] = useState(true);
  const [missingReason, setMissingReason] = useState(false);
  const paid = order.paidAt !== null;
  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('admin.orders.dialogs.cancelTitle', { number: order.order.orderNumber })}
      error={cancel.error}
      pending={cancel.isPending}
      confirmLabel={t('admin.orders.actions.cancel')}
      danger
      onSubmit={() => {
        if (!reason.trim()) {
          setMissingReason(true);
          return;
        }
        setMissingReason(false);
        cancel.mutate(
          {
            reason: reason.trim(),
            customerMessage: message.trim() || undefined,
            restock,
            notifyCustomer: notify,
          },
          { onSuccess: () => onOpenChange(false) },
        );
      }}
    >
      {paid && (
        <Alert tone="info">
          {t(
            order.order.paymentMethod === 'STRIPE'
              ? 'admin.orders.dialogs.refundCard'
              : 'admin.orders.dialogs.refundTransfer',
            { amount: formatMoney(order.order.totals.total, locale) },
          )}
        </Alert>
      )}
      <TextField
        label={t('admin.orders.dialogs.reason')}
        hint={t('admin.orders.dialogs.reasonHint')}
        maxLength={500}
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        error={missingReason ? t('admin.common.required') : undefined}
      />
      <CustomerMessageField value={message} onChange={setMessage} />
      <CheckboxField
        label={t('admin.orders.dialogs.restock')}
        checked={restock}
        onChange={(event) => setRestock(event.target.checked)}
      />
      <NotifyField checked={notify} onChange={setNotify} />
    </ActionDialog>
  );
}
