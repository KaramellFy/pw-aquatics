import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type CardPayment = Schemas['CardPaymentView'];

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
