import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { TextField } from '@/components/ui/Field';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { cn } from '@/lib/cn';
import { useAdjustStock, useCountStock, type AdjustmentInput } from '../api/inventory';

/** What the owner says happened; each maps to a ledger reason (stocktake is its own request). */
const ACTIONS = ['RESTOCK', 'LOSS', 'RETURN', 'ADD', 'REMOVE', 'COUNT'] as const;
type Action = (typeof ACTIONS)[number];

/** The ledger entry an action creates; removals are sent as negative numbers. */
export function toAdjustment(
  action: Exclude<Action, 'COUNT'>,
  quantity: number,
  note: string,
): AdjustmentInput {
  const reason: AdjustmentInput['reason'] =
    action === 'ADD' || action === 'REMOVE' ? 'MANUAL_ADJUSTMENT' : action;
  const removes = action === 'LOSS' || action === 'REMOVE';
  return { delta: removes ? -quantity : quantity, reason, note: note.trim() || undefined };
}

interface StockDialogProps {
  product: { id: string; name: string; stockUnits: number } | null;
  onClose: () => void;
}

/** Records a stock change: restock, loss, return, correction or stocktake. */
export function StockDialog({ product, onClose }: StockDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog
      open={product !== null}
      onOpenChange={(open) => !open && onClose()}
      title={product ? t('admin.inventory.dialog.title', { name: product.name }) : ''}
    >
      {product && <StockForm product={product} onClose={onClose} />}
    </Dialog>
  );
}

function StockForm({
  product,
  onClose,
}: {
  product: { id: string; name: string; stockUnits: number };
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const legendId = useId();
  const adjust = useAdjustStock();
  const count = useCountStock();
  const errorMessage = useApiErrorMessage();
  const [action, setAction] = useState<Action>('RESTOCK');
  const [quantity, setQuantity] = useState('');
  const [note, setNote] = useState('');
  const [invalid, setInvalid] = useState<string | null>(null);
  const mutation = action === 'COUNT' ? count : adjust;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const counting = action === 'COUNT';
    const valid = counting ? /^\d{1,6}$/.test(quantity.trim()) : /^[1-9]\d{0,5}$/.test(quantity.trim());
    if (!valid) {
      setInvalid(
        counting ? t('admin.inventory.dialog.countInvalid') : t('admin.inventory.dialog.quantityInvalid'),
      );
      return;
    }
    setInvalid(null);
    const number = Number(quantity.trim());
    if (action === 'COUNT') {
      count.mutate(
        { productId: product.id, countedUnits: number, note: note.trim() || undefined },
        { onSuccess: onClose },
      );
    } else {
      adjust.mutate(
        { productId: product.id, input: toAdjustment(action, number, note) },
        { onSuccess: onClose },
      );
    }
  };

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <p className="text-ink-soft">{t('admin.inventory.dialog.current', { count: product.stockUnits })}</p>
      {mutation.isError && <Alert tone="error">{errorMessage(mutation.error)}</Alert>}
      <fieldset aria-labelledby={legendId}>
        <legend id={legendId} className="text-sm font-medium text-ink-soft">
          {t('admin.inventory.dialog.type')}
        </legend>
        <div className="mt-2 grid gap-2">
          {ACTIONS.map((value) => (
            <label
              key={value}
              className={cn(
                'flex min-h-11 cursor-pointer items-center gap-3 rounded-xl border px-3 text-[0.9375rem]',
                action === value ? 'border-brand-700 bg-brand-50' : 'border-line-strong',
              )}
            >
              <input
                type="radio"
                name="stock-action"
                value={value}
                checked={action === value}
                onChange={() => setAction(value)}
                className="size-4 accent-brand-700"
              />
              {t(`admin.inventory.dialog.types.${value}`)}
            </label>
          ))}
        </div>
      </fieldset>
      <TextField
        label={
          action === 'COUNT' ? t('admin.inventory.dialog.counted') : t('admin.inventory.dialog.quantity')
        }
        inputMode="numeric"
        value={quantity}
        onChange={(event) => setQuantity(event.target.value)}
        error={invalid ?? undefined}
      />
      <TextField
        label={t('admin.inventory.dialog.note')}
        value={note}
        maxLength={450}
        onChange={(event) => setNote(event.target.value)}
      />
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onClose}>
          {t('admin.common.cancel')}
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? t('admin.common.saving') : t('admin.common.save')}
        </Button>
      </div>
    </form>
  );
}
