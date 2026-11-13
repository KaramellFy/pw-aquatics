import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type CustomerSummary = Schemas['CustomerSummaryView'];
export type CustomerDetail = Schemas['CustomerDetailView'];
export type CustomerStatus = CustomerSummary['status'];

export function useAdminCustomers(query: { q?: string; status?: CustomerStatus; page: number }) {
  const params: Record<string, string> = { page: String(query.page), size: '25' };
  if (query.q?.trim()) params.q = query.q.trim();
  if (query.status) params.status = query.status;
  return useQuery({
    queryKey: queryKeys.adminCustomers(params),
    queryFn: ({ signal }) =>
      api.get<Schemas['PageResponseCustomerSummaryView']>(
        `/admin/customers?${new URLSearchParams(params).toString()}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

export function useAdminCustomer(id: string) {
  return useQuery({
    queryKey: queryKeys.adminCustomer(id),
    queryFn: ({ signal }) => api.get<CustomerDetail>(`/admin/customers/${encodeURIComponent(id)}`, signal),
  });
}

/** Locks (the customer is signed out at once) or unlocks an account. */
export function useSetCustomerLock(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ lock, reason }: { lock: boolean; reason?: string }) =>
      lock
        ? api.post<CustomerDetail>(`/admin/customers/${encodeURIComponent(id)}/lock`, { reason })
        : api.post<CustomerDetail>(`/admin/customers/${encodeURIComponent(id)}/unlock`),
    onSuccess: (customer) => {
      queryClient.setQueryData(queryKeys.adminCustomer(id), customer);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'customers', 'list'] });
    },
  });
}
