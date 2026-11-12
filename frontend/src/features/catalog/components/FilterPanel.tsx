import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/cn';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import type { CategoryNode, Colour, Facets, Grade, SpeciesSummary } from '../api/catalog';
import { toSearchParams, toggle, type AvailabilityKey, type ShopFilters } from '../model/shopFilters';

interface FilterPanelProps {
  filters: ShopFilters;
  onChange: (patch: Partial<ShopFilters>) => void;
  facets: Facets | undefined;
  categories: CategoryNode[] | undefined;
  species: SpeciesSummary[] | undefined;
  colours: Colour[] | undefined;
  grades: Grade[] | undefined;
}

/**
 * Shop filters. Options and counts come from the API facets; an option stays visible
 * while selected even if it has no matches, so it can always be unselected.
 */
export function FilterPanel({
  filters,
  onChange,
  facets,
  categories,
  species,
  colours,
  grades,
}: FilterPanelProps) {
  const { t } = useTranslation();

  const countOf = (list: Facets['species'] | undefined, slug: string) =>
    list?.find((option) => option.slug === slug)?.count ?? 0;
  const visible = <T extends { slug: string }>(
    items: T[] | undefined,
    list: Facets['species'] | undefined,
    selected: string[],
  ) => (items ?? []).filter((item) => countOf(list, item.slug) > 0 || selected.includes(item.slug));

  const speciesOptions = visible(species, facets?.species, filters.species);
  const colourOptions = visible(colours, facets?.colours, filters.colours);
  const gradeOptions = visible(grades, facets?.grades, filters.grades);

  return (
    <div className="space-y-9">
      {categories && categories.length > 0 && (
        <FilterGroup title={t('shop.filters.categories')}>
          <CategoryLinks categories={categories} filters={filters} />
        </FilterGroup>
      )}

      <FilterGroup title={t('shop.filters.availability')}>
        <AvailabilityOptions filters={filters} facets={facets} onChange={onChange} />
      </FilterGroup>

      {speciesOptions.length > 0 && (
        <FilterGroup title={t('shop.filters.species')}>
          {speciesOptions.map((option) => (
            <CheckOption
              key={option.slug}
              checked={filters.species.includes(option.slug)}
              onChange={() => onChange({ species: toggle(filters.species, option.slug) })}
              count={countOf(facets?.species, option.slug)}
            >
              <span>{option.commonName ?? option.scientificName}</span>
              {option.commonName && (
                <span className="block scientific text-xs text-muted">{option.scientificName}</span>
              )}
            </CheckOption>
          ))}
        </FilterGroup>
      )}

      {colourOptions.length > 0 && (
        <FilterGroup title={t('shop.filters.colour')}>
          {colourOptions.map((option) => (
            <CheckOption
              key={option.slug}
              checked={filters.colours.includes(option.slug)}
              onChange={() => onChange({ colours: toggle(filters.colours, option.slug) })}
              count={countOf(facets?.colours, option.slug)}
            >
              <span className="flex items-center gap-2.5">
                <span
                  aria-hidden
                  className="size-4 rounded-full ring-1 ring-ink/15"
                  style={{ backgroundColor: option.hexCode ?? undefined }}
                />
                {option.name}
              </span>
            </CheckOption>
          ))}
        </FilterGroup>
      )}

      {gradeOptions.length > 0 && (
        <FilterGroup title={t('shop.filters.grade')}>
          {gradeOptions.map((option) => (
            <CheckOption
              key={option.slug}
              checked={filters.grades.includes(option.slug)}
              onChange={() => onChange({ grades: toggle(filters.grades, option.slug) })}
              count={countOf(facets?.grades, option.slug)}
            >
              {option.name}
            </CheckOption>
          ))}
        </FilterGroup>
      )}

      <FilterGroup title={t('shop.filters.price')}>
        {/* Re-keyed so the inputs reset when the URL changes (e.g. "Clear all"). */}
        <PriceRange
          key={`${filters.minPrice ?? ''}:${filters.maxPrice ?? ''}`}
          filters={filters}
          facets={facets}
          onChange={onChange}
        />
      </FilterGroup>
    </div>
  );
}

function FilterGroup({ title, children }: { title: string; children: ReactNode }) {
  const id = useId();
  return (
    <section aria-labelledby={id}>
      <h3 id={id} className="mb-2 px-2 font-sans eyebrow text-ink">
        {title}
      </h3>
      <div className="space-y-0.5">{children}</div>
    </section>
  );
}

function CheckOption({
  checked,
  onChange,
  count,
  children,
}: {
  checked: boolean;
  onChange: () => void;
  count: number;
  children: ReactNode;
}) {
  return (
    <label className="flex min-h-11 cursor-pointer items-center gap-3 rounded-lg px-2 text-[0.9375rem] transition-colors hover:bg-mist">
      <input
        type="checkbox"
        checked={checked}
        onChange={onChange}
        className="size-4.5 shrink-0 cursor-pointer rounded border-line-strong accent-brand-700"
      />
      <span className="min-w-0 flex-1 py-1.5">{children}</span>
      <span className="text-sm text-muted tabular-nums">{count}</span>
    </label>
  );
}

function CategoryLinks({ categories, filters }: { categories: CategoryNode[]; filters: ShopFilters }) {
  const { t } = useTranslation();
  // One marker per panel (the desktop sidebar and the phone sheet can both exist).
  const marker = useId();
  // Keep the other filters when switching category, but start on page 1.
  const search = toSearchParams({ ...filters, page: 1 }).toString();
  const link = (slug: string | undefined, label: string, depth: number) => {
    const active = filters.category === slug;
    return (
      <Link
        key={slug ?? 'all'}
        to={{ pathname: slug ? `/shop/${slug}` : '/shop', search }}
        aria-current={active ? 'page' : undefined}
        className={cn(
          'relative flex min-h-11 press items-center rounded-lg px-3 text-[0.9375rem] transition-colors hover:bg-mist',
          depth > 0 && 'pl-7',
          active ? 'font-medium text-brand-800' : 'text-ink-soft',
        )}
      >
        {active && (
          <SlidingIndicator
            layoutId={`category-${marker}`}
            stretch="y"
            className={cn('top-2.5 bottom-2.5 w-0.5 rounded-full', depth > 0 ? 'left-4' : 'left-0')}
            markClassName="bg-brand-500"
          />
        )}
        {label}
      </Link>
    );
  };
  return (
    <nav aria-label={t('shop.filters.categories')}>
      {link(undefined, t('shop.allAnimals'), 0)}
      {categories.flatMap((category) => [
        link(category.slug, category.name, 0),
        ...category.children.map((child) => link(child.slug, child.name, 1)),
      ])}
    </nav>
  );
}

function AvailabilityOptions({
  filters,
  facets,
  onChange,
}: {
  filters: ShopFilters;
  facets: Facets | undefined;
  onChange: (patch: Partial<ShopFilters>) => void;
}) {
  const { t } = useTranslation();
  const name = useId();
  const options: { value: AvailabilityKey; label: string; count?: number }[] = [
    { value: 'all', label: t('shop.filters.availabilityAll') },
    { value: 'in-stock', label: t('shop.filters.availabilityInStock'), count: facets?.availability.inStock },
    {
      value: 'coming-soon',
      label: t('shop.filters.availabilityComingSoon'),
      count: facets?.availability.comingSoon,
    },
  ];
  return (
    <div role="radiogroup" aria-label={t('shop.filters.availability')}>
      {options.map((option) => (
        <label
          key={option.value}
          className="flex min-h-11 cursor-pointer items-center gap-3 rounded-lg px-2 text-[0.9375rem] transition-colors hover:bg-mist"
        >
          <input
            type="radio"
            name={name}
            checked={filters.availability === option.value}
            onChange={() => onChange({ availability: option.value })}
            className="size-4.5 shrink-0 cursor-pointer accent-brand-700"
          />
          <span className="flex-1">{option.label}</span>
          {option.count !== undefined && (
            <span className="text-sm text-muted tabular-nums">{option.count}</span>
          )}
        </label>
      ))}
    </div>
  );
}

function PriceRange({
  filters,
  facets,
  onChange,
}: {
  filters: ShopFilters;
  facets: Facets | undefined;
  onChange: (patch: Partial<ShopFilters>) => void;
}) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const [min, setMin] = useState(filters.minPrice?.toString() ?? '');
  const [max, setMax] = useState(filters.maxPrice?.toString() ?? '');
  const minId = useId();
  const maxId = useId();

  const parse = (value: string) => {
    const number = Number(value.replace(',', '.'));
    return value.trim() !== '' && Number.isFinite(number) && number >= 0 ? number : undefined;
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    onChange({ minPrice: parse(min), maxPrice: parse(max) });
  };

  const input =
    'h-11 w-full rounded-xl border border-line-strong bg-surface px-3 text-[0.9375rem] tabular-nums placeholder:text-muted/80 focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none';

  return (
    <form onSubmit={submit} className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor={minId} className="mb-1 block text-xs text-muted">
            {t('shop.filters.priceMin')}
          </label>
          <input
            id={minId}
            inputMode="decimal"
            value={min}
            onChange={(event) => setMin(event.target.value)}
            placeholder={facets?.price ? formatMoney(facets.price.min, locale) : undefined}
            className={input}
          />
        </div>
        <div>
          <label htmlFor={maxId} className="mb-1 block text-xs text-muted">
            {t('shop.filters.priceMax')}
          </label>
          <input
            id={maxId}
            inputMode="decimal"
            value={max}
            onChange={(event) => setMax(event.target.value)}
            placeholder={facets?.price ? formatMoney(facets.price.max, locale) : undefined}
            className={input}
          />
        </div>
      </div>
      <Button type="submit" variant="secondary" size="sm" className="w-full">
        {t('shop.filters.priceApply')}
      </Button>
    </form>
  );
}
