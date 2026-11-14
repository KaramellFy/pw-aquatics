import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type ShopInfo = Schemas['ShopInfoView'];

/** Shop-wide notices (e.g. a shipping pause); changes rarely, so it is kept for a few minutes. */
export function useShopInfo() {
  return useQuery({
    queryKey: queryKeys.shopInfo,
    queryFn: ({ signal }) => api.get<ShopInfo>('/shop-info', signal),
    staleTime: 5 * 60 * 1000,
  });
}
