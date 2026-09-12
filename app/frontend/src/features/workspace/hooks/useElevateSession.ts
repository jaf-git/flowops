import { useMutation, type UseMutationResult } from '@tanstack/react-query';

import { apiRequest } from '../../../shared/api/client';

interface ReauthenticationWindow {
  reauthenticatedUntil: string;
}

export function useElevateSession(): UseMutationResult<ReauthenticationWindow, Error, string> {
  return useMutation({
    mutationFn: (password: string) =>
      apiRequest<ReauthenticationWindow>('/auth/reauthenticate', {
        method: 'POST',
        body: JSON.stringify({ password }),
      }),
    retry: false,
  });
}
