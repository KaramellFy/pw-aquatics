import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type AdminOrder = Schemas['AdminOrderView'];
export type AdminOrderSummary = Schemas['AdminOrderSummaryView'];
export type AdminOrderStatus = AdminOrderSummary['status'];
export type AdminPaymentStatus = AdminOrderSummary['paymentStatus'];
export type Shipment = Schemas['ShipmentView'];
export type MarkPaidInput = Schemas['MarkPaidRequest'];
export type StatusInput = Schemas['OrderStatusRequest'];
export type CancelInput = Schemas['CancelRequest'];
export type ShipmentInput = Schemas['ShipmentRequest'];
export type ShipmentCorrectionInput = Schemas['ShipmentCorrection'];

export interface OrderListQuery {
  q?: string;
  statuses?: AdminOrderStatus[];
  paymentStatus?: AdminPaymentStatus;
  /** ISO dates (yyyy-mm-dd), days in the shop's time zone. */
  from?: string;
  to?: string;
  oldestFirst?: boolean;
  /** Zero-based. */
  page: number;
}

function toParams(query: OrderListQuery): URLSearchParams {
  const params = new URLSearchParams({ page: String(query.page), size: '25' });
  if (query.q?.trim()) params.set('q', query.q.trim());
  for (const status of query.statuses ?? []) params.append('status', status);
  if (query.paymentStatus) params.set('paymentStatus', query.paymentStatus);
  if (query.from) params.set('from', query.from);
  if (query.to) params.set('to', query.to);
  if (query.oldestFirst) params.set('oldestFirst', 'true');
  return params;
}

export function useAdminOrders(query: OrderListQuery) {
  const params = toParams(query);
  return useQuery({
    queryKey: queryKeys.adminOrders(params.toString()),
    queryFn: ({ signal }) =>
      api.get<Schemas['PageResponseAdminOrderSummaryView']>(`/admin/orders?${params.toString()}`, signal),
    placeholderData: keepPreviousData,
  });
}

export function useAdminOrder(orderNumber: string) {
  return useQuery({
    queryKey: queryKeys.adminOrder(orderNumber),
    queryFn: ({ signal }) => api.get<AdminOrder>(`/admin/orders/${encodeURIComponent(orderNumber)}`, signal),
  });
}

const path = (orderNumber: string) => `/admin/orders/${encodeURIComponent(orderNumber)}`;

/**
 * Every order action answers with the full order. It replaces the cached order; lists, the
 * dashboard and customer pages are refreshed, and so is the stock if animals went back on sale.
 */
function useOrderMutation<TVariables>(
  orderNumber: string,
  mutationFn: (variables: TVariables) => Promise<AdminOrder>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (order) => {
      queryClient.setQueryData(queryKeys.adminOrder(orderNumber), order);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'orders', 'list'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminDashboard });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'stats'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'customers'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
    },
  });
}

/** A bank transfer has arrived. */
export function useMarkPaid(orderNumber: string) {
  return useOrderMutation(orderNumber, (input: MarkPaidInput) =>
    api.post<AdminOrder>(`${path(orderNumber)}/mark-paid`, input),
  );
}

/** Being packed (PROCESSING) or arrived (DELIVERED). */
export function useChangeOrderStatus(orderNumber: string) {
  return useOrderMutation(orderNumber, (input: StatusInput) =>
    api.post<AdminOrder>(`${path(orderNumber)}/status`, input),
  );
}

export function useCancelByShop(orderNumber: string) {
  return useOrderMutation(orderNumber, (input: CancelInput) =>
    api.post<AdminOrder>(`${path(orderNumber)}/cancel`, input),
  );
}

export function useAddOrderNote(orderNumber: string) {
  return useOrderMutation(orderNumber, (body: string) =>
    api.post<AdminOrder>(`${path(orderNumber)}/notes`, { body }),
  );
}

export function useAddShipment(orderNumber: string) {
  return useOrderMutation(orderNumber, (input: ShipmentInput) =>
    api.post<AdminOrder>(`${path(orderNumber)}/shipments`, input),
  );
}

export function useCorrectShipment(orderNumber: string) {
  return useOrderMutation(orderNumber, ({ id, input }: { id: string; input: ShipmentCorrectionInput }) =>
    api.put<AdminOrder>(`${path(orderNumber)}/shipments/${encodeURIComponent(id)}`, input),
  );
}

export function useRemoveShipment(orderNumber: string) {
  return useOrderMutation(orderNumber, (id: string) =>
    api.delete<AdminOrder>(`${path(orderNumber)}/shipments/${encodeURIComponent(id)}`),
  );
}
