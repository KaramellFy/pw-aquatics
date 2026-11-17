import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type CardPayment = Schemas['CardPaymentView'];
export type PayPalPayment = Schemas['PayPalPaymentView'];
export type PayPalCapture = Schemas['PayPalCaptureView'];

/**
 * What Stripe.js needs to show the payment form for one of the customer's orders. The server
 * creates the PaymentIntent for the stored order total on the first call and returns the same
 * one afterwards, so loading this again (reload, second attempt) is safe. Only the order
 * number is sent — never an amount.
 */
export function useCardPayment(orderNumber: string) {
  return useQuery({
    queryKey: queryKeys.cardPayment(orderNumber),
    queryFn: () => api.post<CardPayment>('/payments/stripe/intents', { orderNumber }),
    // The client secret stays valid; a new request would only return the same PaymentIntent.
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    retry: false,
  });
}

/**
 * What PayPal's button needs for one of the customer's orders. The server creates the PayPal
 * order for the stored order total on the first call and returns the same one afterwards. Only
 * the order number is sent — never an amount.
 */
export function usePayPalPayment(orderNumber: string) {
  return useQuery({
    queryKey: queryKeys.payPalPayment(orderNumber),
    queryFn: () => api.post<PayPalPayment>('/payments/paypal/orders', { orderNumber }),
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    retry: false,
  });
}

/**
 * After the customer approved in PayPal's window: our server takes the money and reads the
 * result from PayPal. Whether the order is paid comes from the server's answer, never from PayPal's
 * window.
 */
export function useCapturePayPalPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderNumber: string) =>
      api.post<PayPalCapture>('/payments/paypal/captures', { orderNumber }),
    onSuccess: (capture) => {
      if (capture.status !== 'DECLINED') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.allOrders });
      }
    },
  });
}
