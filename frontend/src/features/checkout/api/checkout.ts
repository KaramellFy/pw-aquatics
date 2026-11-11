import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { useNavigate } from 'react-router';
import { ApiError, api, apiRequest } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type ShippingOptions = Schemas['ShippingOptions'];
export type ShippingOption = Schemas['ShippingOption'];
export type Quote = Schemas['QuoteView'];
export type QuoteLine = Schemas['QuoteLineView'];
export type PlaceOrderInput = Schemas['PlaceOrderRequest'];
export type PlacedOrder = Schemas['PlacedOrderView'];

/**
 * Shipping methods the server offers for the customer's cart and a destination country,
 * with prices and dispatch dates.
 */
export function useShippingMethods(countryCode: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.shippingMethods(countryCode ?? ''),
    queryFn: ({ signal }) =>
      api.get<ShippingOptions>(
        `/checkout/shipping-methods?country=${encodeURIComponent(countryCode ?? '')}`,
        signal,
      ),
    enabled: enabled && Boolean(countryCode),
    staleTime: 0,
    retry: false,
  });
}

/**
 * The binding price breakdown for the cart, an address and a shipping method. It is always
 * fetched fresh: prices, stock and dispatch dates can change at any time.
 */
export function useQuote(addressId: string | undefined, methodCode: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.quote(addressId ?? '', methodCode ?? ''),
    queryFn: () =>
      api.post<Quote>('/checkout/quote', { shippingAddressId: addressId, shippingMethodCode: methodCode }),
    enabled: enabled && Boolean(addressId) && Boolean(methodCode),
    staleTime: 0,
    gcTime: 0,
    refetchOnWindowFocus: true,
    retry: false,
  });
}

function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

/**
 * Places the order and moves on to the confirmation page.
 *
 * Every attempt carries an idempotency key. If the answer gets lost (network error, server
 * hiccup) the same key is sent again, so the retry returns the order created the first
 * time instead of ordering twice. Only when the server clearly refused the attempt does
 * the next one get a new key.
 */
export function usePlaceOrder() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const idempotencyKey = useRef<string | null>(null);

  return useMutation({
    mutationFn: async (request: PlaceOrderInput) => {
      idempotencyKey.current ??= newIdempotencyKey();
      const placed = await apiRequest<PlacedOrder>('/checkout/orders', {
        method: 'POST',
        body: request,
        headers: { 'Idempotency-Key': idempotencyKey.current },
      });
      // Leave the checkout before the (now empty) cart is refreshed.
      await navigate(`/checkout/complete/${placed.orderNumber}`, { replace: true });
      return placed;
    },
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.checkout });
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
      void queryClient.invalidateQueries({ queryKey: queryKeys.allOrders });
      // Stock has changed.
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
    },
    onError: (error) => {
      const refused = error instanceof ApiError && error.status >= 400 && error.status < 500;
      if (refused) idempotencyKey.current = null;
      // Show what the server says now: new total, changed cart, other shipping options.
      void queryClient.invalidateQueries({ queryKey: queryKeys.checkout });
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}
