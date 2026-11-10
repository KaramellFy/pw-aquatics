import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type Cart = Schemas['CartView'];
export type CartLine = Schemas['CartLineView'];
export type CartWarning = Schemas['CartWarningView'];

/**
 * The cart as calculated by the server (prices, totals, availability). The UI never
 * computes any of these; every change returns the full recalculated cart.
 */
export function useCart() {
  return useQuery({
    queryKey: queryKeys.cart,
    queryFn: ({ signal }) => api.get<Cart>('/cart', signal),
    staleTime: 30_000,
    // Stock and prices may have changed while the tab was in the background.
    refetchOnWindowFocus: true,
  });
}

function useCartMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<Cart>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (cart) => queryClient.setQueryData(queryKeys.cart, cart),
    // After a refused change (e.g. stock ran out) show what the server has now.
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.cart }),
  });
}

export function useAddToCart() {
  return useCartMutation((request: Schemas['AddCartItemRequest']) => api.post<Cart>('/cart/items', request));
}

export function useUpdateCartItem() {
  return useCartMutation(({ itemId, quantity }: { itemId: string; quantity: number }) =>
    api.patch<Cart>(`/cart/items/${encodeURIComponent(itemId)}`, { quantity }),
  );
}

export function useRemoveCartItem() {
  return useCartMutation((itemId: string) => api.delete<Cart>(`/cart/items/${encodeURIComponent(itemId)}`));
}

export function useClearCart() {
  return useCartMutation(() => api.delete<Cart>('/cart'));
}
