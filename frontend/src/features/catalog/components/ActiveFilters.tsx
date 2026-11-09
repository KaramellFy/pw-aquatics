import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Colour, Grade, SpeciesSummary } from '../api/catalog';
import type { ShopFilters } from '../model/shopFilters';

interface ActiveFiltersProps {
  filters: ShopFilters;
  onChange: (patch: Partial<ShopFilters>) => void;
  onClear: () => void;
  species: SpeciesSummary[] | undefined;
  colours: Colour[] | undefined;
  grades: Grade[] | undefined;
}

interface Chip {
  key: string;
  label: string;
  remove: Partial<ShopFilters>;
}

/** Removable chips for every active filter. */
export function ActiveFilters({ filters, onChange, onClear, species, colours, grades }: ActiveFiltersProps) {
  const { t } = useTranslation();
  const nameOf = <T extends { slug: string }>(
    items: T[] | undefined,
    slug: string,
    label: (item: T) => string,
  ) => {
    const item = items?.find((candidate) => candidate.slug === slug);
    return item ? label(item) : slug;
  };

  const chips: Chip[] = [
    ...(filters.q ? [{ key: 'q', label: `“${filters.q}”`, remove: { q: undefined } }] : []),
    ...filters.species.map((slug) => ({
      key: `species-${slug}`,
      label: nameOf(species, slug, (s) => s.commonName ?? s.scientificName),
      remove: { species: filters.species.filter((v) => v !== slug) },
    })),
    ...filters.colours.map((slug) => ({
      key: `colour-${slug}`,
      label: nameOf(colours, slug, (c) => c.name),
      remove: { colours: filters.colours.filter((v) => v !== slug) },
    })),
    ...filters.grades.map((slug) => ({
      key: `grade-${slug}`,
      label: t('product.grade', { grade: nameOf(grades, slug, (g) => g.name) }),
      remove: { grades: filters.grades.filter((v) => v !== slug) },
    })),
    ...(filters.availability !== 'all'
      ? [
          {
            key: 'availability',
            label:
              filters.availability === 'in-stock'
                ? t('shop.filters.availabilityInStock')
                : t('shop.filters.availabilityComingSoon'),
            remove: { availability: 'all' as const },
          },
        ]
      : []),
    ...(filters.minPrice !== undefined || filters.maxPrice !== undefined
      ? [
          {
            key: 'price',
            label: `€ ${filters.minPrice ?? 0} – ${filters.maxPrice ?? '…'}`,
            remove: { minPrice: undefined, maxPrice: undefined },
          },
        ]
      : []),
  ];

  if (chips.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-2">
      {chips.map((chip) => (
        <button
          key={chip.key}
          type="button"
          onClick={() => onChange(chip.remove)}
          aria-label={t('shop.filters.remove', { label: chip.label })}
          className="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-brand-200 bg-brand-50 py-1 pr-2.5 pl-3.5 text-sm text-ink transition-colors hover:border-brand-400 active:bg-brand-100"
        >
          {chip.label}
          <X aria-hidden className="size-3.5 text-ink-soft" />
        </button>
      ))}
      <button
        type="button"
        onClick={onClear}
        className="min-h-9 px-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
      >
        {t('shop.filters.clearAll')}
      </button>
    </div>
  );
}
