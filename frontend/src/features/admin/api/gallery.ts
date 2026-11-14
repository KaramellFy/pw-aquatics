import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, apiRequest } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type GalleryImage = Schemas['AdminGalleryImageView'];
export type GalleryImageInput = Schemas['GalleryImageRequest'];

export function useAdminGallery() {
  return useQuery({
    queryKey: queryKeys.adminGallery,
    queryFn: ({ signal }) => api.get<GalleryImage[]>('/admin/gallery', signal),
  });
}

/** Changes also refresh the public gallery page. */
function useGalleryMutation<TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminGallery });
      void queryClient.invalidateQueries({ queryKey: ['content', 'gallery'] });
    },
  });
}

export function useUploadGalleryImage() {
  return useGalleryMutation(
    ({
      file,
      altText,
      title,
      caption,
    }: {
      file: File;
      altText: string;
      title?: string;
      caption?: string;
    }) => {
      const form = new FormData();
      form.append('file', file);
      form.append('altText', altText);
      if (title) form.append('title', title);
      if (caption) form.append('caption', caption);
      return apiRequest<GalleryImage>('/admin/gallery', { method: 'POST', body: form });
    },
  );
}

export function useUpdateGalleryImage() {
  return useGalleryMutation(({ id, input }: { id: string; input: GalleryImageInput }) =>
    api.put<GalleryImage>(`/admin/gallery/${encodeURIComponent(id)}`, input),
  );
}

export function useReorderGallery() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (imageIds: string[]) => api.patch<GalleryImage[]>('/admin/gallery/order', { imageIds }),
    onSuccess: (images) => {
      queryClient.setQueryData(queryKeys.adminGallery, images);
      void queryClient.invalidateQueries({ queryKey: ['content', 'gallery'] });
    },
  });
}

export function useDeleteGalleryImage() {
  return useGalleryMutation((id: string) => api.delete<void>(`/admin/gallery/${encodeURIComponent(id)}`));
}
