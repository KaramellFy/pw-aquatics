import { Search, SlidersHorizontal } from 'lucide-react';
import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { SORT_KEYS, type ShopFilters, type SortKey } from '../model/shopFilters';

interface ShopToolbarProps {
  filters: ShopFilters;
  onChange: (patch: Partial<ShopFilters>) => void;
  activeFilters: number;
  onOpenFilters: () => void;
}

export function ShopToolbar({ filters, onChange, activeFilters, onOpenFilters }: ShopToolbarProps) {
  const { t } = useTranslation();
  const sortId = useId();
  const searchId = useId();
  const [query, setQuery] = useState(filters.q ?? '');

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onChange({ q: query.trim() || undefined });
  };

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
      <form role="search" onSubmit={submit} className="relative flex-1">
        <label htmlFor={searchId} className="sr-only">
          {t('shop.searchLabel')}
        </label>
        <Search
          aria-hidden
          className="pointer-events-none absolute top-1/2 left-4 size-4 -translate-y-1/2 text-muted"
        />
        <input
          id={searchId}
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t('shop.searchPlaceholder')}
          className="h-11 w-full rounded-full border border-line-strong bg-surface pr-4 pl-11 text-[0.9375rem] placeholder:text-muted/80 focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none"
        />
      </form>
      <div className="flex items-center gap-3">
        <Button variant="secondary" className="lg:hidden" onClick={onOpenFilters}>
          <SlidersHorizontal aria-hidden className="size-4" />
          {activeFilters > 0
            ? t('shop.filters.openWithCount', { count: activeFilters })
            : t('shop.filters.open')}
        </Button>
        <label htmlFor={sortId} className="sr-only">
          {t('shop.sort.label')}
        </label>
        <select
          id={sortId}
          value={filters.sort}
          onChange={(event) => onChange({ sort: event.target.value as SortKey })}
          className="h-11 flex-1 rounded-full border border-line-strong bg-surface px-4 text-[0.9375rem] focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none sm:flex-none"
        >
          {SORT_KEYS.map((key) => (
            <option key={key} value={key}>
              {t(`shop.sort.${key}`)}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
