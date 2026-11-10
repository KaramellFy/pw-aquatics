import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type Session = Schemas['SessionView'];
export type CurrentUser = Schemas['CurrentUserView'];

const ANONYMOUS: Session = { authenticated: false, user: null };

/** Who is signed in. The server session is the source of truth; nothing is kept in browser storage. */
export function useSession() {
  return useQuery({
    queryKey: queryKeys.session,
    queryFn: ({ signal }) => api.get<Session>('/auth/me', signal),
    staleTime: 5 * 60_000,
    // Notice a sign-out that happened in another tab or on another device.
    refetchOnWindowFocus: true,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: Schemas['LoginRequest']) => api.post<Session>('/auth/login', request),
    onSuccess: (session) => {
      queryClient.removeQueries({ queryKey: queryKeys.account });
      queryClient.setQueryData(queryKeys.session, session);
      // The server merges the guest cart into the account's cart on the next cart request.
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}

/**
 * Signs out and goes to the home page. The page is left *before* the session is cleared
 * locally; otherwise the account area's guard would redirect to the login page first.
 */
export function useLogout() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: async () => {
      try {
        await api.post<void>('/auth/logout');
      } finally {
        await navigate('/');
      }
    },
    onSettled: () => {
      queryClient.setQueryData(queryKeys.session, ANONYMOUS);
      queryClient.removeQueries({ queryKey: queryKeys.account });
      // Signed out: the account's cart is no longer shown.
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: (request: Schemas['RegisterRequest']) => api.post<void>('/auth/register', request),
  });
}

export function useVerifyEmail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (token: string) => api.post<void>('/auth/verify-email', { token }),
    // An email change alters the signed-in user's address.
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.session }),
        queryClient.invalidateQueries({ queryKey: queryKeys.account }),
      ]),
  });
}

export function useResendVerification() {
  return useMutation({
    mutationFn: (email: string) => api.post<void>('/auth/resend-verification', { email }),
  });
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: (email: string) => api.post<void>('/auth/forgot-password', { email }),
  });
}

export function useResetPassword() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: Schemas['ResetPasswordRequest']) => api.post<void>('/auth/reset-password', request),
    onSuccess: () => {
      queryClient.setQueryData(queryKeys.session, ANONYMOUS);
      queryClient.removeQueries({ queryKey: queryKeys.account });
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}
