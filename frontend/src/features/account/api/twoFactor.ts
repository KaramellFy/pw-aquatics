import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type TwoFactorStatus = Schemas['TwoFactorStatusView'];
export type TwoFactorSetup = Schemas['TwoFactorSetupView'];

/** Whether two-step sign-in is set up (administrators only; the server refuses customers). */
export function useTwoFactorStatus(enabled = true) {
  return useQuery({
    queryKey: queryKeys.twoFactor,
    queryFn: ({ signal }) => api.get<TwoFactorStatus>('/account/two-factor', signal),
    enabled,
  });
}

/**
 * Asks for a new key for the authenticator app. Replacing an app that works needs a current code
 * (or a recovery code); the new key is used only after its first code was confirmed.
 */
export function useStartTwoFactorSetup() {
  return useMutation({
    mutationFn: (code?: string) =>
      api.post<TwoFactorSetup>('/account/two-factor/setup', code ? { code } : {}),
  });
}

/**
 * Confirms the new key with its first code. The server answers with ten recovery codes (shown
 * once), opens the admin area for this session and signs out the account's other sessions.
 */
export function useConfirmTwoFactorSetup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) =>
      api.post<Schemas['RecoveryCodesView']>('/account/two-factor/confirm', { code }),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.session }),
        queryClient.invalidateQueries({ queryKey: queryKeys.twoFactor }),
      ]),
  });
}

/** Ten new recovery codes for a current code; the old ones stop working. */
export function useRenewRecoveryCodes() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) =>
      api.post<Schemas['RecoveryCodesView']>('/account/two-factor/recovery-codes', { code }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.twoFactor }),
  });
}
