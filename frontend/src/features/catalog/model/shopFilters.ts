import { majorToMinorUnits } from '@/lib/format/money';
import type { ProductQuery } from '../api/catalog';

/**
 * Shop filters as they appear in the URL. URLs use readable values
 * (`/shop/shrimp?colour=blue&availability=in-stock&min=3&sort=price-asc&page=2`);
 * this module translates them to and from API query parameters. No business rules here.
 */

export const SORT_KEYS = ['relevance', 'newest', 'price-asc', 'price-desc', 'name', 'grade'] as const;
export type SortKey = (typeof SORT_KEYS)[number];

export const AVAILABILITY_KEYS = ['all', 'in-stock', 'coming-soon'] as const;
export type AvailabilityKey = (typeof AVAILABILITY_KEYS)[number];

export const PAGE_SIZE = 24;

export interface ShopFilters {
  category?: string;
  q?: string;
  species: string[];
  colours: string[];
  grades: string[];
  availability: AvailabilityKey;
  /** In major currency units (€), as typed by the shopper. */
  minPrice?: number;
  maxPrice?: number;
  sort: SortKey;
  /** 1-based. */
  page: number;
}

const API_SORT: Record<SortKey, string> = {
  relevance: 'RELEVANCE',
  newest: 'NEWEST',
  'price-asc': 'PRICE_ASC',
  'price-desc': 'PRICE_DESC',
  name: 'NAME_ASC',
  grade: 'GRADE_DESC',
};

const API_AVAILABILITY: Record<AvailabilityKey, string | undefined> = {
  all: undefined,
  'in-stock': 'IN_STOCK',
  'coming-soon': 'COMING_SOON',
};

function list(params: URLSearchParams, key: string): string[] {
  return params
    .getAll(key)
    .flatMap((value) => value.split(','))
    .map((value) => value.trim())
    .filter(Boolean);
}

function positiveNumber(value: string | null): number | undefined {
  if (value === null || value.trim() === '') return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
}

function oneOf<T extends string>(value: string | null, allowed: readonly T[], fallback: T): T {
  return allowed.includes(value as T) ? (value as T) : fallback;
}

export function parseShopFilters(params: URLSearchParams, category?: string): ShopFilters {
  const page = Math.floor(positiveNumber(params.get('page')) ?? 1);
  return {
    category: category || undefined,
    q: params.get('q')?.trim() || undefined,
    species: list(params, 'species'),
    colours: list(params, 'colour'),
    grades: list(params, 'grade'),
    availability: oneOf(params.get('availability'), AVAILABILITY_KEYS, 'all'),
    minPrice: positiveNumber(params.get('min')),
    maxPrice: positiveNumber(params.get('max')),
    sort: oneOf(params.get('sort'), SORT_KEYS, 'relevance'),
    page: page >= 1 ? page : 1,
  };
}

/** URL search params for the filters (the category lives in the path). Defaults are omitted. */
export function toSearchParams(filters: ShopFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.q) params.set('q', filters.q);
  if (filters.species.length) params.set('species', filters.species.join(','));
  if (filters.colours.length) params.set('colour', filters.colours.join(','));
  if (filters.grades.length) params.set('grade', filters.grades.join(','));
  if (filters.availability !== 'all') params.set('availability', filters.availability);
  if (filters.minPrice !== undefined) params.set('min', String(filters.minPrice));
  if (filters.maxPrice !== undefined) params.set('max', String(filters.maxPrice));
  if (filters.sort !== 'relevance') params.set('sort', filters.sort);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

/** Query for the facets endpoint: the same filters, without sorting and paging. */
export function toFacetQuery(filters: ShopFilters): ProductQuery {
  const query: ProductQuery = {};
  if (filters.category) query.category = filters.category;
  if (filters.q) query.q = filters.q;
  if (filters.species.length) query.species = filters.species.join(',');
  if (filters.colours.length) query.colour = filters.colours.join(',');
  if (filters.grades.length) query.grade = filters.grades.join(',');
  const availability = API_AVAILABILITY[filters.availability];
  if (availability) query.availability = availability;
  if (filters.minPrice !== undefined) query.minPrice = String(majorToMinorUnits(filters.minPrice));
  if (filters.maxPrice !== undefined) query.maxPrice = String(majorToMinorUnits(filters.maxPrice));
  return query;
}

/** Query for the product list endpoint. */
export function toProductQuery(filters: ShopFilters): ProductQuery {
  const query = toFacetQuery(filters);
  if (filters.sort !== 'relevance') query.sort = API_SORT[filters.sort];
  if (filters.page > 1) query.page = String(filters.page - 1);
  query.size = String(PAGE_SIZE);
  return query;
}

/** Number of active filters (shown on the mobile "Filters" button). */
export function activeFilterCount(filters: ShopFilters): number {
  return (
    filters.species.length +
    filters.colours.length +
    filters.grades.length +
    (filters.availability !== 'all' ? 1 : 0) +
    (filters.minPrice !== undefined || filters.maxPrice !== undefined ? 1 : 0)
  );
}

export function toggle(values: string[], value: string): string[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value];
}
