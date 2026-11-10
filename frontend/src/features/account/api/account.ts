import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type Profile = Schemas['ProfileView'];
export type Address = Schemas['AddressView'];
export type AddressInput = Schemas['AddressRequest'];

const ANONYMOUS: Schemas['SessionView'] = { authenticated: false, user: null };

export function useProfile() {
  return useQuery({
    queryKey: queryKeys.profile,
    queryFn: ({ signal }) => api.get<Profile>('/account/profile', signal),
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: Schemas['UpdateProfileRequest']) => api.patch<Profile>('/account/profile', request),
    onSuccess: (profile) => {
      queryClient.setQueryData(queryKeys.profile, profile);
      // The header greets the user by name.
      return queryClient.invalidateQueries({ queryKey: queryKeys.session });
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (request: Schemas['ChangePasswordRequest']) => api.put<void>('/account/password', request),
  });
}

export function useChangeEmail() {
  return useMutation({
    mutationFn: (request: Schemas['ChangeEmailRequest']) => api.put<void>('/account/email', request),
  });
}

/** Deletes the account, then leaves the account area before the session is cleared locally. */
export function useDeleteAccount() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: async (request: Schemas['DeleteAccountRequest']) => {
      await api.delete<void>('/account', request);
      await navigate('/', { replace: true });
    },
    onSuccess: () => {
      queryClient.setQueryData(queryKeys.session, ANONYMOUS);
      queryClient.removeQueries({ queryKey: queryKeys.account });
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}

export function useAddresses() {
  return useQuery({
    queryKey: queryKeys.addresses,
    queryFn: ({ signal }) => api.get<Address[]>('/account/addresses', signal),
  });
}

/** Creates an address, or updates it when `id` is given. */
export function useSaveAddress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, address }: { id?: string; address: AddressInput }) =>
      id
        ? api.put<Address>(`/account/addresses/${encodeURIComponent(id)}`, address)
        : api.post<Address>('/account/addresses', address),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.addresses }),
  });
}

export function useDeleteAddress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/account/addresses/${encodeURIComponent(id)}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.addresses }),
  });
}

export function useSetDefaultAddress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...defaults }: { id: string } & Schemas['DefaultAddressRequest']) =>
      api.put<Address[]>(`/account/addresses/${encodeURIComponent(id)}/default`, defaults),
    // The server returns the full, updated list.
    onSuccess: (addresses) => queryClient.setQueryData(queryKeys.addresses, addresses),
  });
}
