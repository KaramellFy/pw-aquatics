import { useState, type FormEvent } from 'react';
import { Download } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/cn';
import { salesExportUrl } from '../../api/statistics';
import { periodPresets, type Period, type PeriodPreset } from '../../data/periods';

/**
 * The one filter row of the dashboard: preset periods first, a custom range behind them, and
 * the CSV download of the chosen period. Everything below it shows the same period.
 */
export function PeriodFilter({
  period,
  preset,
  onPreset,
  onCustom,
}: {
  period: Period;
  preset: PeriodPreset | null;
  onPreset: (preset: PeriodPreset) => void;
  onCustom: (period: Period) => void;
}) {
  const { t } = useTranslation();
  const [custom, setCustom] = useState(preset === null);
  const [from, setFrom] = useState(period.from);
  const [to, setTo] = useState(period.to);
  const [error, setError] = useState<string | null>(null);

  const apply = (event: FormEvent) => {
    event.preventDefault();
    if (!from || !to) {
      setError(t('admin.dashboard.period.missing'));
      return;
    }
    if (to < from) {
      setError(t('admin.dashboard.period.order'));
      return;
    }
    setError(null);
    onCustom({ from, to });
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <div role="group" aria-label={t('admin.dashboard.period.label')} className="flex flex-wrap gap-2">
          {periodPresets.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={!custom && preset === value}
              onClick={() => {
                setCustom(false);
                onPreset(value);
              }}
              className={cn(
                'min-h-11 rounded-full border px-4 text-sm transition-colors',
                !custom && preset === value
                  ? 'border-ink bg-ink font-medium text-white'
                  : 'border-line bg-surface text-ink-soft hover:border-line-strong',
              )}
            >
              {t(`admin.dashboard.period.presets.${value}`)}
            </button>
          ))}
          <button
            type="button"
            aria-pressed={custom}
            aria-expanded={custom}
            aria-controls="dashboard-custom-period"
            onClick={() => {
              setFrom(period.from);
              setTo(period.to);
              setCustom((value) => !value);
            }}
            className={cn(
              'min-h-11 rounded-full border px-4 text-sm transition-colors',
              custom
                ? 'border-ink bg-ink font-medium text-white'
                : 'border-line bg-surface text-ink-soft hover:border-line-strong',
            )}
          >
            {t('admin.dashboard.period.custom')}
          </button>
        </div>
        <a
          href={salesExportUrl(period)}
          download
          className="ml-auto inline-flex min-h-11 items-center gap-2 rounded-full px-4 text-sm font-medium text-brand-800 hover:bg-ink/5"
        >
          <Download aria-hidden className="size-4" />
          {t('admin.dashboard.export')}
        </a>
      </div>

      {custom && (
        <form
          id="dashboard-custom-period"
          onSubmit={apply}
          noValidate
          className="flex flex-wrap items-end gap-3 rounded-card border border-line bg-surface p-4"
        >
          <label className="flex flex-col gap-1 text-sm text-ink-soft">
            {t('admin.dashboard.period.from')}
            <input
              type="date"
              value={from}
              onChange={(event) => setFrom(event.target.value)}
              className="min-h-11 rounded-xl border border-line-strong bg-surface px-3 text-ink"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-ink-soft">
            {t('admin.dashboard.period.to')}
            <input
              type="date"
              value={to}
              onChange={(event) => setTo(event.target.value)}
              className="min-h-11 rounded-xl border border-line-strong bg-surface px-3 text-ink"
            />
          </label>
          <Button type="submit" variant="secondary">
            {t('admin.dashboard.period.apply')}
          </Button>
          {error && (
            <p role="alert" className="w-full text-sm text-danger">
              {error}
            </p>
          )}
        </form>
      )}
    </div>
  );
}
