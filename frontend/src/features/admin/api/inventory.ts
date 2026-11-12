import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type StockMovement = Schemas['StockMovementView'];
export type StockLevel = Schemas['StockLevelView'];
export type AdjustmentInput = Schemas['AdjustmentRequest'];

const path = (productId: string) => `/admin/inventory/${encodeURIComponent(productId)}`;

export function useStockMovements(productId: string, page: number) {
  return useQuery({
    queryKey: queryKeys.adminMovements(productId, page),
    queryFn: ({ signal }) =>
      api.get<Schemas['PageResponseStockMovementView']>(
        `${path(productId)}/movements?page=${page}&size=25`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

function useStockMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<StockLevel>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (level) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'movements', level.productId] });
      // Availability in the shop depends on the stock.
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
    },
  });
}

/** Adds (positive delta) or removes (negative delta) animals with a reason. */
export function useAdjustStock() {
  return useStockMutation(({ productId, input }: { productId: string; input: AdjustmentInput }) =>
    api.post<StockLevel>(`${path(productId)}/adjustments`, input),
  );
}

/** Stocktake: the counted number becomes the stock. */
export function useCountStock() {
  return useStockMutation(
    ({ productId, countedUnits, note }: { productId: string; countedUnits: number; note?: string }) =>
      api.put<StockLevel>(`${path(productId)}/count`, { countedUnits, note }),
  );
}
