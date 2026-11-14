import { useInfiniteQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type PublishedGalleryImage = Schemas['GalleryImageView'];

const PAGE_SIZE = 24;

/** Published gallery photos in the owner's order, a page at a time. */
export function usePublishedGallery() {
  return useInfiniteQuery({
    queryKey: queryKeys.gallery(PAGE_SIZE),
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      api.get<Schemas['PageResponseGalleryImageView']>(
        `/gallery?page=${pageParam}&size=${PAGE_SIZE}`,
        signal,
      ),
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}
