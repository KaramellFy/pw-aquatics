import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, apiRequest } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type AdminProduct = Schemas['AdminProductView'];
export type AdminProductSummary = Schemas['AdminProductSummaryView'];
export type AdminVariant = Schemas['AdminVariantView'];
export type AdminImage = Schemas['AdminImageView'];
export type ProductInput = Schemas['ProductRequest'];
export type VariantInput = Schemas['VariantRequest'];
export type ProductStatus = AdminProduct['status'];
export type StockFilter = 'ALL' | 'LOW_STOCK' | 'OUT_OF_STOCK';
export type ProductSort = 'UPDATED_DESC' | 'NAME_ASC' | 'STOCK_ASC';

export interface ProductListQuery {
  q?: string;
  status?: ProductStatus;
  categoryId?: string;
  stock?: StockFilter;
  sort?: ProductSort;
  /** Zero-based. */
  page: number;
  size?: number;
}

function toParams(query: ProductListQuery): Record<string, string> {
  const params: Record<string, string> = { page: String(query.page), size: String(query.size ?? 25) };
  if (query.q?.trim()) params.q = query.q.trim();
  if (query.status) params.status = query.status;
  if (query.categoryId) params.categoryId = query.categoryId;
  if (query.stock && query.stock !== 'ALL') params.stock = query.stock;
  if (query.sort) params.sort = query.sort;
  return params;
}

/** Admin product list (also the stock overview), with exact stock numbers. */
export function useAdminProducts(query: ProductListQuery) {
  const params = toParams(query);
  return useQuery({
    queryKey: queryKeys.adminProducts(params),
    queryFn: ({ signal }) =>
      api.get<Schemas['PageResponseAdminProductSummaryView']>(
        `/admin/products?${new URLSearchParams(params).toString()}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

export function useAdminProduct(id: string) {
  return useQuery({
    queryKey: queryKeys.adminProduct(id),
    queryFn: ({ signal }) => api.get<AdminProduct>(`/admin/products/${encodeURIComponent(id)}`, signal),
  });
}

/**
 * Every product change answers with the full product. It replaces the cached product, and
 * lists plus the public catalog are refreshed, so the shop shows the change at once.
 */
function useProductMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<AdminProduct>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (product) => {
      queryClient.setQueryData(queryKeys.adminProduct(product.id), product);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'products', 'list'] });
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
    },
  });
}

const path = (id: string) => `/admin/products/${encodeURIComponent(id)}`;

export function useCreateProduct() {
  return useProductMutation((input: ProductInput) => api.post<AdminProduct>('/admin/products', input));
}

export function useUpdateProduct(id: string) {
  return useProductMutation((input: ProductInput) => api.put<AdminProduct>(path(id), input));
}

export function usePublishProduct(id: string) {
  return useProductMutation(() => api.post<AdminProduct>(`${path(id)}/publish`));
}

export function useArchiveProduct(id: string) {
  return useProductMutation(() => api.post<AdminProduct>(`${path(id)}/archive`));
}

export function useDuplicateProduct(id: string) {
  return useProductMutation(() => api.post<AdminProduct>(`${path(id)}/duplicate`));
}

export function useAddVariant(productId: string) {
  return useProductMutation((input: VariantInput) =>
    api.post<AdminProduct>(`${path(productId)}/variants`, input),
  );
}

export function useUpdateVariant(productId: string) {
  return useProductMutation(({ variantId, input }: { variantId: string; input: VariantInput }) =>
    api.put<AdminProduct>(`${path(productId)}/variants/${encodeURIComponent(variantId)}`, input),
  );
}

export function useUploadImage(productId: string) {
  return useProductMutation(({ file, altText }: { file: File; altText: string }) => {
    const form = new FormData();
    form.append('file', file);
    form.append('altText', altText);
    return apiRequest<AdminProduct>(`${path(productId)}/images`, { method: 'POST', body: form });
  });
}

export function useUpdateAltText(productId: string) {
  return useProductMutation(({ imageId, altText }: { imageId: string; altText: string }) =>
    api.patch<AdminProduct>(`${path(productId)}/images/${encodeURIComponent(imageId)}`, { altText }),
  );
}

export function useReorderImages(productId: string) {
  return useProductMutation((imageIds: string[]) =>
    api.patch<AdminProduct>(`${path(productId)}/images/order`, { imageIds }),
  );
}

export function useDeleteImage(productId: string) {
  return useProductMutation((imageId: string) =>
    api.delete<AdminProduct>(`${path(productId)}/images/${encodeURIComponent(imageId)}`),
  );
}

/** Renders Markdown exactly as the shop will (sanitised on the server). */
export function useMarkdownPreview() {
  return useMutation({
    mutationFn: (markdown: string) =>
      api.post<Schemas['MarkdownPreviewView']>('/admin/markdown/preview', { markdown }),
  });
}
