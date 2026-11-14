import { Plus } from 'lucide-react';
import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { CheckboxField, TextAreaField, TextField } from '@/components/ui/Field';
import { Price } from '@/components/ui/Price';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatCalendarDate } from '@/lib/format/date';
import { moneyInputValue, parseMoneyInput } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import {
  useAdminShippingMethods,
  useSaveShippingMethod,
  useSaveShippingPause,
  useShippingPause,
  type AdminShippingMethod,
  type ShippingPause,
  type Weekday,
} from '../api/shipping';
import { AdminCard } from '../components/AdminCard';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { DataTable, Td, Th } from '../components/DataTable';
import { LoadState } from '../components/LoadState';
import { Pill } from '../components/StatusBadge';

const WEEKDAYS: Weekday[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** Shipping methods (price, countries, dispatch weekdays) and the shipping pause for live animals. */
export function AdminShippingPage() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const methods = useAdminShippingMethods();
  const pause = useShippingPause();
  const [editing, setEditing] = useState<AdminShippingMethod | 'new' | null>(null);
  const [editKey, setEditKey] = useState(0);
  const open = (method: AdminShippingMethod | 'new') => {
    setEditKey((key) => key + 1);
    setEditing(method);
  };
  const regionName = new Intl.DisplayNames([locale], { type: 'region' });

  return (
    <>
      <AdminPageHeader title={t('admin.shipping.title')} lead={t('admin.shipping.lead')} />

      <LoadState
        isPending={pause.isPending}
        isError={pause.isError}
        hasData={Boolean(pause.data)}
        onRetry={() => void pause.refetch()}
      />
      {pause.data && <PauseCard key={JSON.stringify(pause.data)} pause={pause.data} />}

      <AdminCard
        title={t('admin.shipping.methods')}
        id="shipping-methods"
        className="mt-6"
        actions={
          <Button variant="secondary" onClick={() => open('new')}>
            <Plus aria-hidden className="size-4" />
            {t('admin.shipping.add')}
          </Button>
        }
      >
        <LoadState
          isPending={methods.isPending}
          isError={methods.isError}
          hasData={Boolean(methods.data)}
          onRetry={() => void methods.refetch()}
        />
        {methods.data && (
          <DataTable
            caption={t('admin.shipping.methods')}
            head={
              <tr>
                <Th>{t('admin.shipping.columns.method')}</Th>
                <Th className="text-right">{t('admin.shipping.columns.price')}</Th>
                <Th>{t('admin.shipping.columns.dispatch')}</Th>
                <Th>{t('admin.shipping.columns.countries')}</Th>
                <Th>
                  <span className="sr-only">{t('admin.common.actions')}</span>
                </Th>
              </tr>
            }
          >
            {methods.data.map((method) => (
              <tr key={method.id} className="hover:bg-mist/60">
                <Td>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-ink">{method.name}</span>
                    {!method.active && <Pill>{t('admin.shipping.inactive')}</Pill>}
                    {method.liveAnimalsAllowed && <Pill tone="ok">{t('admin.shipping.liveAnimals')}</Pill>}
                  </div>
                  <p className="text-sm text-muted">
                    {[method.code, method.carrier].filter(Boolean).join(' · ')}
                  </p>
                </Td>
                <Td className="text-right">
                  <Price value={method.price} />
                  {method.freeFrom && (
                    <span className="block text-xs whitespace-nowrap text-muted">
                      {t('admin.shipping.freeFromShort')} <Price value={method.freeFrom} />
                    </span>
                  )}
                </Td>
                <Td className="text-sm text-ink-soft">
                  {method.dispatchDays.map((day) => t(`admin.shipping.weekdaysShort.${day}`)).join(', ')}
                </Td>
                <Td className="max-w-[16rem] text-sm text-ink-soft">
                  <span title={method.countries.map((code) => regionName.of(code) ?? code).join(', ')}>
                    {method.countries.join(', ')}
                  </span>
                </Td>
                <Td className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('admin.common.editLabel', { name: method.name })}
                    onClick={() => open(method)}
                  >
                    {t('admin.common.edit')}
                  </Button>
                </Td>
              </tr>
            ))}
          </DataTable>
        )}
      </AdminCard>

      <MethodDialog key={editKey} method={editing} onClose={() => setEditing(null)} />
    </>
  );
}

function PauseCard({ pause }: { pause: ShippingPause }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const save = useSaveShippingPause();
  const errorMessage = useApiErrorMessage();
  const [active, setActive] = useState(pause.active);
  const [message, setMessage] = useState(pause.message ?? '');
  const [until, setUntil] = useState(pause.until ?? '');

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (save.isPending) return;
    save.mutate({ active, message: message.trim() || undefined, until: until || undefined });
  };

  return (
    <AdminCard title={t('admin.shipping.pause.title')} id="shipping-pause">
      <p className="-mt-2 mb-5 max-w-2xl text-sm text-ink-soft">{t('admin.shipping.pause.lead')}</p>
      {pause.inForce ? (
        <Alert tone="info" className="mb-5">
          {pause.until
            ? t('admin.shipping.pause.inForceUntil', {
                date: formatCalendarDate(pause.until, locale, 'full'),
              })
            : t('admin.shipping.pause.inForceOpen')}
        </Alert>
      ) : (
        pause.active && (
          <Alert tone="info" className="mb-5">
            {t('admin.shipping.pause.ended')}
          </Alert>
        )
      )}
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        {save.isSuccess && (
          <p role="status" className="text-sm text-success">
            {t('admin.common.saved')}
          </p>
        )}
        <CheckboxField
          label={t('admin.shipping.pause.active')}
          checked={active}
          onChange={(event) => setActive(event.target.checked)}
        />
        <div className="grid gap-5 md:grid-cols-[1fr_16rem]">
          <TextAreaField
            label={`${t('admin.shipping.pause.message')} (${t('admin.common.optional')})`}
            hint={t('admin.shipping.pause.messageHint')}
            rows={2}
            maxLength={300}
            value={message}
            onChange={(event) => setMessage(event.target.value)}
          />
          <TextField
            type="date"
            label={t('admin.shipping.pause.until')}
            hint={t('admin.shipping.pause.untilHint')}
            value={until}
            onChange={(event) => setUntil(event.target.value)}
          />
        </div>
        <Button
          type="submit"
          aria-disabled={save.isPending || undefined}
          aria-busy={save.isPending || undefined}
        >
          {save.isPending ? t('admin.common.saving') : t('admin.common.save')}
        </Button>
      </form>
    </AdminCard>
  );
}

interface MethodForm {
  code: string;
  name: string;
  description: string;
  carrier: string;
  price: string;
  freeFrom: string;
  liveAnimalsAllowed: boolean;
  dispatchDays: Weekday[];
  estimatedDaysMin: string;
  estimatedDaysMax: string;
  countries: string;
  active: boolean;
  sortOrder: string;
}

function initialForm(method: AdminShippingMethod | null): MethodForm {
  return {
    code: method?.code ?? '',
    name: method?.name ?? '',
    description: method?.description ?? '',
    carrier: method?.carrier ?? 'DHL',
    price: method ? moneyInputValue(method.price.amount) : '',
    freeFrom: method?.freeFrom ? moneyInputValue(method.freeFrom.amount) : '',
    liveAnimalsAllowed: method?.liveAnimalsAllowed ?? true,
    dispatchDays: method?.dispatchDays ?? ['MONDAY', 'TUESDAY', 'WEDNESDAY'],
    estimatedDaysMin: method?.estimatedDaysMin?.toString() ?? '',
    estimatedDaysMax: method?.estimatedDaysMax?.toString() ?? '',
    countries: method?.countries.join(', ') ?? '',
    active: method?.active ?? true,
    sortOrder: method?.sortOrder.toString() ?? '0',
  };
}

function MethodDialog({
  method,
  onClose,
}: {
  method: AdminShippingMethod | 'new' | null;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const legendId = useId();
  const save = useSaveShippingMethod();
  const errorMessage = useApiErrorMessage();
  const existing = method && method !== 'new' ? method : null;
  const [form, setForm] = useState<MethodForm>(() => initialForm(existing));
  const [errors, setErrors] = useState<Partial<Record<keyof MethodForm, string>>>({});
  const set = <K extends keyof MethodForm>(key: K, value: MethodForm[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (save.isPending) return;
    const found: Partial<Record<keyof MethodForm, string>> = {};
    const price = parseMoneyInput(form.price);
    const freeFrom = form.freeFrom.trim() ? parseMoneyInput(form.freeFrom) : undefined;
    const countries = form.countries
      .split(/[\s,;]+/)
      .map((code) => code.trim().toUpperCase())
      .filter(Boolean);
    const whole = (text: string) =>
      text.trim() === '' ? undefined : /^\d{1,2}$/.test(text.trim()) ? Number(text) : NaN;
    const min = whole(form.estimatedDaysMin);
    const max = whole(form.estimatedDaysMax);
    const sortOrder = whole(form.sortOrder) ?? 0;
    if (!existing && !/^[A-Z][A-Z0-9_]{1,49}$/.test(form.code.trim()))
      found.code = t('admin.shipping.form.codeInvalid');
    if (!form.name.trim()) found.name = t('admin.common.required');
    if (price === null) found.price = t('admin.common.numberInvalid');
    if (freeFrom === null || freeFrom === 0) found.freeFrom = t('admin.common.numberInvalid');
    if (form.dispatchDays.length === 0) found.dispatchDays = t('admin.shipping.form.daysRequired');
    if (countries.length === 0 || countries.some((code) => !/^[A-Z]{2}$/.test(code))) {
      found.countries = t('admin.shipping.form.countriesInvalid');
    }
    if (Number.isNaN(min)) found.estimatedDaysMin = t('admin.common.wholeNumberInvalid');
    if (Number.isNaN(max)) found.estimatedDaysMax = t('admin.common.wholeNumberInvalid');
    if (Number.isNaN(sortOrder)) found.sortOrder = t('admin.common.wholeNumberInvalid');
    setErrors(found);
    if (Object.keys(found).length > 0 || price === null) return;
    save.mutate(
      {
        id: existing?.id,
        input: {
          code: existing ? undefined : form.code.trim(),
          name: form.name.trim(),
          description: form.description.trim() || undefined,
          carrier: form.carrier.trim() || undefined,
          priceCents: price,
          freeShippingThresholdCents: freeFrom ?? undefined,
          liveAnimalsAllowed: form.liveAnimalsAllowed,
          dispatchDays: form.dispatchDays,
          estimatedDaysMin: min,
          estimatedDaysMax: max,
          countries,
          active: form.active,
          sortOrder,
          version: existing?.version ?? 0,
        },
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog
      open={method !== null}
      onOpenChange={(isOpen) => !isOpen && onClose()}
      title={
        existing
          ? t('admin.shipping.form.editTitle', { name: existing.name })
          : t('admin.shipping.form.newTitle')
      }
      className="max-w-2xl"
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        <div className="grid gap-5 sm:grid-cols-2">
          <TextField
            label={t('admin.shipping.form.name')}
            maxLength={100}
            value={form.name}
            onChange={(event) => set('name', event.target.value)}
            error={errors.name}
          />
          {existing ? (
            <TextField
              label={t('admin.shipping.form.code')}
              value={existing.code}
              readOnly
              hint={t('admin.shipping.form.codeFixed')}
            />
          ) : (
            <TextField
              label={t('admin.shipping.form.code')}
              hint={t('admin.shipping.form.codeHint')}
              maxLength={50}
              autoCapitalize="characters"
              spellCheck={false}
              value={form.code}
              onChange={(event) => set('code', event.target.value.toUpperCase())}
              error={errors.code}
            />
          )}
          <TextField
            label={t('admin.shipping.form.price')}
            inputMode="decimal"
            value={form.price}
            onChange={(event) => set('price', event.target.value)}
            error={errors.price}
          />
          <TextField
            label={`${t('admin.shipping.form.freeFrom')} (${t('admin.common.optional')})`}
            hint={t('admin.shipping.form.freeFromHint')}
            inputMode="decimal"
            value={form.freeFrom}
            onChange={(event) => set('freeFrom', event.target.value)}
            error={errors.freeFrom}
          />
          <TextField
            label={t('admin.shipping.form.carrier')}
            maxLength={50}
            value={form.carrier}
            onChange={(event) => set('carrier', event.target.value)}
          />
          <TextField
            label={t('admin.common.sortOrder')}
            hint={t('admin.common.sortOrderHint')}
            inputMode="numeric"
            value={form.sortOrder}
            onChange={(event) => set('sortOrder', event.target.value)}
            error={errors.sortOrder}
          />
          <TextField
            label={`${t('admin.shipping.form.daysMin')} (${t('admin.common.optional')})`}
            inputMode="numeric"
            value={form.estimatedDaysMin}
            onChange={(event) => set('estimatedDaysMin', event.target.value)}
            error={errors.estimatedDaysMin}
          />
          <TextField
            label={`${t('admin.shipping.form.daysMax')} (${t('admin.common.optional')})`}
            inputMode="numeric"
            value={form.estimatedDaysMax}
            onChange={(event) => set('estimatedDaysMax', event.target.value)}
            error={errors.estimatedDaysMax}
          />
        </div>
        <TextAreaField
          label={`${t('admin.common.description')} (${t('admin.common.optional')})`}
          rows={2}
          maxLength={500}
          value={form.description}
          onChange={(event) => set('description', event.target.value)}
        />
        <TextField
          label={t('admin.shipping.form.countries')}
          hint={t('admin.shipping.form.countriesHint')}
          value={form.countries}
          onChange={(event) => set('countries', event.target.value)}
          error={errors.countries}
        />
        <fieldset
          aria-labelledby={legendId}
          aria-describedby={errors.dispatchDays ? `${legendId}-error` : undefined}
        >
          <legend id={legendId} className="text-sm font-medium text-ink-soft">
            {t('admin.shipping.form.dispatchDays')}
          </legend>
          <p className="mt-1 text-sm text-muted">{t('admin.shipping.form.dispatchDaysHint')}</p>
          <div className="mt-2 flex flex-wrap gap-x-5 gap-y-2">
            {WEEKDAYS.map((day) => (
              <CheckboxField
                key={day}
                label={t(`admin.shipping.weekdays.${day}`)}
                checked={form.dispatchDays.includes(day)}
                onChange={(event) =>
                  set(
                    'dispatchDays',
                    event.target.checked
                      ? WEEKDAYS.filter((d) => d === day || form.dispatchDays.includes(d))
                      : form.dispatchDays.filter((d) => d !== day),
                  )
                }
              />
            ))}
          </div>
          {errors.dispatchDays && (
            <p id={`${legendId}-error`} className="mt-2 text-sm text-danger">
              {errors.dispatchDays}
            </p>
          )}
        </fieldset>
        <CheckboxField
          label={t('admin.shipping.form.liveAnimals')}
          checked={form.liveAnimalsAllowed}
          onChange={(event) => set('liveAnimalsAllowed', event.target.checked)}
        />
        <CheckboxField
          label={t('admin.shipping.form.active')}
          checked={form.active}
          onChange={(event) => set('active', event.target.checked)}
        />
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
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
