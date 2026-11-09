import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/api/client';

/** Request body of `POST /api/v1/contact` (see docs/API.md §2.3). */
export interface ContactMessageRequest {
  name: string;
  email: string;
  subject: string;
  message: string;
  orderNumber?: string;
  /** Honeypot: humans leave it empty; the server silently drops filled submissions. */
  website?: string;
}

export function sendContactMessage(request: ContactMessageRequest): Promise<void> {
  return api.post<void>('/contact', request);
}

export function useSendContactMessage() {
  return useMutation({ mutationFn: sendContactMessage });
}
