import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';
import type { Period } from '../data/periods';

export type Dashboard = Schemas['DashboardView'];
export type SalesStats = Schemas['SalesStatsView'];
export type ProductStats = Schemas['ProductStatsView'];
export type StockStats = Schemas['StockStatsView'];
export type SalesBucket = Schemas['SalesBucketView'];
export type Granularity = Schemas['StatsPeriodView']['granularity'];
export type CountKpi = Schemas['CountKpiView'];
export type MoneyKpi = Schemas['MoneyKpiView'];
export type ShareKpi = Schemas['ShareKpiView'];

const range = ({ from, to }: Period) => `from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;

/** What needs attention now: open orders, stock running out, the latest orders. */
export function useDashboard() {
  return useQuery({
    queryKey: queryKeys.adminDashboard,
    queryFn: ({ signal }) => api.get<Dashboard>('/admin/dashboard', signal),
  });
}

/** Sales figures of a period, compared with the period before. Keeps the old data while loading. */
export function useSalesStats(period: Period) {
  return useQuery({
    queryKey: queryKeys.adminStats('sales', period.from, period.to),
    queryFn: ({ signal }) => api.get<SalesStats>(`/admin/stats/sales?${range(period)}`, signal),
    placeholderData: keepPreviousData,
  });
}

/** Best sellers and revenue by category and species. */
export function useProductStats(period: Period) {
  return useQuery({
    queryKey: queryKeys.adminStats('products', period.from, period.to),
    queryFn: ({ signal }) => api.get<ProductStats>(`/admin/stats/products?${range(period)}&limit=10`, signal),
    placeholderData: keepPreviousData,
  });
}

/** Restocks, sales and losses from the stock ledger. */
export function useStockStats(period: Period) {
  return useQuery({
    queryKey: queryKeys.adminStats('stock', period.from, period.to),
    queryFn: ({ signal }) => api.get<StockStats>(`/admin/stats/stock?${range(period)}`, signal),
    placeholderData: keepPreviousData,
  });
}

/** Address of the CSV download of daily sales (a plain link; the session cookie authorises it). */
export function salesExportUrl(period: Period): string {
  return `/api/v1/admin/stats/sales/export?${range(period)}`;
}
