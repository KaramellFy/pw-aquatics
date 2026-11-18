import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { ApiError, api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type Session = Schemas['SessionView'];
export type CurrentUser = Schemas['CurrentUserView'];

const ANONYMOUS: Session = {
  authenticated: false,
  user: null,
  twoFactorRequired: false,
  twoFactorSetupRequired: false,
};

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

/** Whether the shop offers signing in with Google (set up by the owner). */
export function useSignInOptions() {
  return useQuery({
    queryKey: queryKeys.signInOptions,
    queryFn: ({ signal }) => api.get<Schemas['SignInOptionsView']>('/auth/sign-in-options', signal),
    staleTime: 60 * 60_000,
  });
}

/**
 * Signs in with email and password. For an administrator with two-step sign-in the answer is
 * not signed in yet but `twoFactorRequired`; {@link useSecondStep} finishes it.
 */
export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: Schemas['LoginRequest']) => api.post<Session>('/auth/login', request),
    onSuccess: (session) => signedIn(queryClient, session),
  });
}

/** The second step of signing in: a code from the authenticator app or a recovery code. */
export function useSecondStep() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => api.post<Session>('/auth/login/two-factor', { code }),
    onSuccess: (session) => signedIn(queryClient, session),
    onError: (error) => {
      // Too many wrong codes, or too slow: the sign-in starts again with the password.
      if (error instanceof ApiError && error.code === 'TWO_FACTOR_EXPIRED') {
        queryClient.setQueryData(queryKeys.session, ANONYMOUS);
      }
    },
  });
}

/** Gives up a sign-in that waits for its code (to sign in with another account). */
export function useCancelSignIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<void>('/auth/logout'),
    onSettled: () => queryClient.setQueryData(queryKeys.session, ANONYMOUS),
  });
}

function signedIn(queryClient: QueryClient, session: Session) {
  queryClient.removeQueries({ queryKey: queryKeys.account });
  queryClient.setQueryData(queryKeys.session, session);
  // The server merges the guest cart into the account's cart on the next cart request.
  void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
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
