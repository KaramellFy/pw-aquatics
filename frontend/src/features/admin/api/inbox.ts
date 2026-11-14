import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type ContactMessage = Schemas['ContactMessageView'];
export type ContactStatus = ContactMessage['status'];

/** @param status null for the inbox: everything except spam */
export function useInbox(status: ContactStatus | null, page: number) {
  const params = new URLSearchParams({ page: String(page), size: '20' });
  if (status) params.set('status', status);
  return useQuery({
    queryKey: queryKeys.adminInbox(status ?? 'INBOX', page),
    queryFn: ({ signal }) =>
      api.get<Schemas['PageResponseContactMessageView']>(
        `/admin/contact-messages?${params.toString()}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

export function useInboxCounts() {
  return useQuery({
    queryKey: queryKeys.adminInboxCounts,
    queryFn: ({ signal }) =>
      api.get<Partial<Record<ContactStatus, number>>>('/admin/contact-messages/counts', signal),
  });
}

export function useSetMessageStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: ContactStatus }) =>
      api.patch<ContactMessage>(`/admin/contact-messages/${encodeURIComponent(id)}`, { status }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin', 'inbox'] }),
  });
}
