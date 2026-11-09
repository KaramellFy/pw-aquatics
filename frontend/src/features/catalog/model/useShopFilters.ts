import { useCallback, useMemo } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { parseShopFilters, toSearchParams, type ShopFilters } from './shopFilters';

/** Shop filter state, stored in the URL so results are shareable and survive reloads. */
export function useShopFilters() {
  const { categorySlug } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const filters = useMemo(() => parseShopFilters(searchParams, categorySlug), [searchParams, categorySlug]);

  const update = useCallback(
    (patch: Partial<ShopFilters>) => {
      // Any change other than paging starts again on page 1.
      const next: ShopFilters = { ...filters, ...patch, page: patch.page ?? 1 };
      navigate(
        {
          pathname: next.category ? `/shop/${encodeURIComponent(next.category)}` : '/shop',
          search: toSearchParams(next).toString(),
        },
        { preventScrollReset: patch.page === undefined },
      );
    },
    [filters, navigate],
  );

  const clear = useCallback(
    () =>
      update({
        q: undefined,
        species: [],
        colours: [],
        grades: [],
        availability: 'all',
        minPrice: undefined,
        maxPrice: undefined,
      }),
    [update],
  );

  return { filters, update, clear };
}
