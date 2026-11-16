import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';
import type { Period } from '../data/periods';

export type AdminCoupon = Schemas['AdminCouponView'];
export type CouponInput = Schemas['CouponRequest'];
export type CouponStatus = AdminCoupon['status'];
export type CouponType = AdminCoupon['type'];
export type CouponUsage = Schemas['CouponUsageView'];
export type CouponRedemption = Schemas['CouponRedemptionView'];
export type CouponRedemptionPage = Schemas['PageResponseCouponRedemptionView'];
export type CouponStats = Schemas['CouponStatsView'];

/** All coupons, newest first; the list page filters them by status. */
export function useAdminCoupons() {
  return useQuery({
    queryKey: queryKeys.adminCoupons,
    queryFn: ({ signal }) => api.get<AdminCoupon[]>('/admin/coupons', signal),
  });
}

export function useAdminCoupon(id: string) {
  return useQuery({
    queryKey: queryKeys.adminCoupon(id),
    queryFn: ({ signal }) => api.get<AdminCoupon>(`/admin/coupons/${encodeURIComponent(id)}`, signal),
  });
}

/** Creates (without id) or changes a coupon. Carts are recalculated by the server on their next read. */
export function useSaveCoupon() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: CouponInput }) =>
      id
        ? api.put<AdminCoupon>(`/admin/coupons/${encodeURIComponent(id)}`, input)
        : api.post<AdminCoupon>('/admin/coupons', input),
    onSuccess: (coupon) => {
      queryClient.setQueryData(queryKeys.adminCoupon(coupon.id), coupon);
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminCoupons });
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}

/** Deletes a coupon no order has used (the server refuses others with COUPON_IN_USE). */
export function useDeleteCoupon() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/admin/coupons/${encodeURIComponent(id)}`),
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: queryKeys.adminCoupon(id) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminCoupons });
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}

/** Orders that used the coupon, what it saved customers and those orders' revenue. */
export function useCouponUsage(id: string) {
  return useQuery({
    queryKey: queryKeys.adminCouponUsage(id),
    queryFn: ({ signal }) => api.get<CouponUsage>(`/admin/coupons/${encodeURIComponent(id)}/usage`, signal),
  });
}

/** Orders that used the coupon, newest first (page is zero-based). */
export function useCouponRedemptions(id: string, page: number) {
  return useQuery({
    queryKey: queryKeys.adminCouponRedemptions(id, page),
    queryFn: ({ signal }) =>
      api.get<CouponRedemptionPage>(
        `/admin/coupons/${encodeURIComponent(id)}/redemptions?page=${page}&size=20`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

/** Coupon figures of a period for the dashboard, compared with the period before. */
export function useCouponStats(period: Period) {
  return useQuery({
    queryKey: queryKeys.adminStats('coupons', period.from, period.to),
    queryFn: ({ signal }) =>
      api.get<CouponStats>(
        `/admin/stats/coupons?from=${encodeURIComponent(period.from)}&to=${encodeURIComponent(period.to)}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}
