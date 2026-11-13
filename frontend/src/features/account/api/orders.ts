import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type OrderSummary = Schemas['OrderSummaryView'];
export type Order = Schemas['OrderDetailView'];
export type OrderStatus = Order['status'];
export type OrderTotals = Schemas['OrderTotalsView'];
export type PaymentInstructions = Schemas['PaymentInstructionsView'];

export const ORDERS_PAGE_SIZE = 10;

/** One page of the customer's order history, newest first. `page` is zero-based. */
export function useOrders(page: number) {
  return useQuery({
    queryKey: queryKeys.orders(page),
    queryFn: ({ signal }) =>
      api.get<Schemas['PageResponseOrderSummaryView']>(
        `/account/orders?page=${page}&size=${ORDERS_PAGE_SIZE}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

interface UseOrderOptions {
  /**
   * Right after paying by card: until this time (epoch ms), ask every two seconds whether the
   * server has heard from Stripe that the payment went through.
   */
  awaitCardPaymentUntil?: number;
}

/** One order with everything the server knows about it: status, totals, whether it can be cancelled. */
export function useOrder(orderNumber: string, { awaitCardPaymentUntil }: UseOrderOptions = {}) {
  return useQuery({
    queryKey: queryKeys.order(orderNumber),
    queryFn: ({ signal }) => api.get<Order>(`/account/orders/${encodeURIComponent(orderNumber)}`, signal),
    // The status changes on the shop's side (payment received, shipped).
    refetchOnWindowFocus: true,
    refetchInterval: (query) => {
      const order = query.state.data;
      const waiting = order?.paymentMethod === 'STRIPE' && order.status === 'PENDING_PAYMENT';
      return waiting && awaitCardPaymentUntil !== undefined && Date.now() < awaitCardPaymentUntil
        ? 2000
        : false;
    },
  });
}

export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderNumber: string) =>
      api.post<Order>(`/account/orders/${encodeURIComponent(orderNumber)}/cancel`),
    onSuccess: (order) => {
      queryClient.setQueryData(queryKeys.order(order.orderNumber), order);
      // The history shows the status too; the animals are back in stock.
      void queryClient.invalidateQueries({ queryKey: queryKeys.allOrders, refetchType: 'none' });
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
    },
    // If cancelling was refused, show what the order looks like now.
    onError: (_error, orderNumber) =>
      queryClient.invalidateQueries({ queryKey: queryKeys.order(orderNumber) }),
  });
}
