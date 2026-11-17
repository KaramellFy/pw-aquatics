import { describe, expect, it } from 'vitest';
import {
  activeFilterCount,
  canonicalPage,
  parseShopFilters,
  toFacetQuery,
  toProductQuery,
  toSearchParams,
  toggle,
} from './shopFilters';

describe('shop filters', () => {
  it('parses readable URL params with safe defaults', () => {
    const filters = parseShopFilters(
      new URLSearchParams(
        'colour=blue,red&grade=high&availability=in-stock&min=3&max=6.5&sort=price-asc&page=2',
      ),
      'shrimp',
    );
    expect(filters).toEqual({
      category: 'shrimp',
      q: undefined,
      species: [],
      colours: ['blue', 'red'],
      grades: ['high'],
      availability: 'in-stock',
      minPrice: 3,
      maxPrice: 6.5,
      sort: 'price-asc',
      page: 2,
    });
  });

  it('ignores invalid values instead of failing', () => {
    const filters = parseShopFilters(new URLSearchParams('availability=soon&sort=cheap&page=-4&min=abc'));
    expect(filters.availability).toBe('all');
    expect(filters.sort).toBe('relevance');
    expect(filters.page).toBe(1);
    expect(filters.minPrice).toBeUndefined();
  });

  it('round-trips through the URL and omits defaults', () => {
    const params = new URLSearchParams('colour=blue&availability=coming-soon&page=3');
    expect(toSearchParams(parseShopFilters(params)).toString()).toBe(
      'colour=blue&availability=coming-soon&page=3',
    );
    expect(toSearchParams(parseShopFilters(new URLSearchParams())).toString()).toBe('');
  });

  it('translates to API parameters (cents, enums, zero-based pages)', () => {
    const filters = parseShopFilters(
      new URLSearchParams(
        'species=neocaridina-davidi&colour=blue&availability=in-stock&min=3&max=5.9&sort=grade&page=2',
      ),
      'shrimp',
    );
    expect(toProductQuery(filters)).toEqual({
      category: 'shrimp',
      species: 'neocaridina-davidi',
      colour: 'blue',
      availability: 'IN_STOCK',
      minPrice: '300',
      maxPrice: '590',
      sort: 'GRADE_DESC',
      page: '1',
      size: '24',
    });
    expect(toFacetQuery(filters)).not.toHaveProperty('sort');
    expect(toFacetQuery(filters)).not.toHaveProperty('page');
  });

  it('counts active filters and toggles values', () => {
    const filters = parseShopFilters(new URLSearchParams('colour=blue,red&min=2&availability=in-stock'));
    expect(activeFilterCount(filters)).toBe(4);
    expect(toggle(['a', 'b'], 'a')).toEqual(['b']);
    expect(toggle(['a'], 'b')).toEqual(['a', 'b']);
  });

  it('names list pages of their own only for the plain list', () => {
    const page = (search: string) => canonicalPage(parseShopFilters(new URLSearchParams(search), 'shrimp'));
    expect(page('page=3')).toBe(3);
    expect(page('page=3&utm_source=news')).toBe(3);
    expect(page('')).toBe(1);
    // Filtered, searched or sorted views point to the plain list's first page.
    expect(page('colour=blue&page=3')).toBe(1);
    expect(page('q=blue&page=3')).toBe(1);
    expect(page('sort=price-asc&page=3')).toBe(1);
  });
});
