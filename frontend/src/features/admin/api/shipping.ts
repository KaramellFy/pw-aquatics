import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type AdminShippingMethod = Schemas['AdminShippingMethodView'];
export type ShippingMethodInput = Schemas['ShippingMethodRequest'];
export type Weekday = ShippingMethodInput['dispatchDays'][number];
export type ShippingPause = Schemas['ShippingPauseView'];
export type ShippingPauseInput = Schemas['ShippingPauseRequest'];

export function useAdminShippingMethods() {
  return useQuery({
    queryKey: queryKeys.adminShippingMethods,
    queryFn: ({ signal }) => api.get<AdminShippingMethod[]>('/admin/shipping-methods', signal),
  });
}

/** Creates (without id) or changes a method; checkout options and the shop info follow at once. */
export function useSaveShippingMethod() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: ShippingMethodInput }) =>
      id
        ? api.put<AdminShippingMethod>(`/admin/shipping-methods/${encodeURIComponent(id)}`, input)
        : api.post<AdminShippingMethod>('/admin/shipping-methods', input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminShippingMethods });
      void queryClient.invalidateQueries({ queryKey: queryKeys.checkout });
    },
  });
}

export function useShippingPause() {
  return useQuery({
    queryKey: queryKeys.adminShippingPause,
    queryFn: ({ signal }) => api.get<ShippingPause>('/admin/settings/shipping-pause', signal),
  });
}

export function useSaveShippingPause() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ShippingPauseInput) =>
      api.put<ShippingPause>('/admin/settings/shipping-pause', input),
    onSuccess: (pause) => {
      queryClient.setQueryData(queryKeys.adminShippingPause, pause);
      void queryClient.invalidateQueries({ queryKey: queryKeys.shopInfo });
      void queryClient.invalidateQueries({ queryKey: queryKeys.checkout });
    },
  });
}
