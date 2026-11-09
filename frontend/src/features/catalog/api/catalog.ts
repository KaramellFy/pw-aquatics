import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type ProductSummary = Schemas['ProductSummaryView'];
export type ProductDetail = Schemas['ProductDetailView'];
export type ProductPage = Schemas['PageResponseProductSummaryView'];
export type Facets = Schemas['FacetsView'];
export type CategoryNode = Schemas['CategoryNodeView'];
export type SpeciesSummary = Schemas['SpeciesSummaryView'];
export type SpeciesDetail = Schemas['SpeciesView'];
export type Colour = Schemas['ColourView'];
export type Grade = Schemas['GradeView'];
export type Availability = ProductSummary['availability'];

/** API query parameters for /products and /products/facets (see model/shopFilters.ts). */
export type ProductQuery = Record<string, string>;

const TAXONOMY_STALE_TIME = 10 * 60_000;

function withQuery(path: string, query: ProductQuery): string {
  const params = new URLSearchParams(query).toString();
  return params ? `${path}?${params}` : path;
}

export function useProducts(query: ProductQuery) {
  return useQuery({
    queryKey: queryKeys.products(query),
    queryFn: ({ signal }) => api.get<ProductPage>(withQuery('/products', query), signal),
    placeholderData: keepPreviousData,
  });
}

export function useFacets(query: ProductQuery) {
  return useQuery({
    queryKey: queryKeys.facets(query),
    queryFn: ({ signal }) => api.get<Facets>(withQuery('/products/facets', query), signal),
    placeholderData: keepPreviousData,
  });
}

export function useProduct(slug: string) {
  return useQuery({
    queryKey: queryKeys.product(slug),
    queryFn: ({ signal }) => api.get<ProductDetail>(`/products/${encodeURIComponent(slug)}`, signal),
  });
}

export function useRelatedProducts(slug: string) {
  return useQuery({
    queryKey: queryKeys.related(slug),
    queryFn: ({ signal }) =>
      api.get<ProductSummary[]>(`/products/${encodeURIComponent(slug)}/related`, signal),
  });
}

export function useCategories() {
  return useQuery({
    queryKey: queryKeys.categories,
    queryFn: ({ signal }) => api.get<CategoryNode[]>('/categories', signal),
    staleTime: TAXONOMY_STALE_TIME,
  });
}

export function useSpeciesList() {
  return useQuery({
    queryKey: queryKeys.speciesList,
    queryFn: ({ signal }) => api.get<SpeciesSummary[]>('/species', signal),
    staleTime: TAXONOMY_STALE_TIME,
  });
}

export function useSpecies(slug: string) {
  return useQuery({
    queryKey: queryKeys.species(slug),
    queryFn: ({ signal }) => api.get<SpeciesDetail>(`/species/${encodeURIComponent(slug)}`, signal),
  });
}

export function useColours() {
  return useQuery({
    queryKey: queryKeys.colours,
    queryFn: ({ signal }) => api.get<Colour[]>('/colours', signal),
    staleTime: TAXONOMY_STALE_TIME,
  });
}

export function useGrades() {
  return useQuery({
    queryKey: queryKeys.grades,
    queryFn: ({ signal }) => api.get<Grade[]>('/grades', signal),
    staleTime: TAXONOMY_STALE_TIME,
  });
}
