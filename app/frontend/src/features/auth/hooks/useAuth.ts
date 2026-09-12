import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import { ApiError } from '../../../shared/api/client';
import { rememberServerTime } from '../../../shared/lib/serverClock';
import {
  changePassword,
  completePasswordReset,
  completeSignup,
  fetchOwnSessions,
  fetchSessionContext,
  fetchUserSessions,
  login,
  logout,
  reauthenticate,
  requestPasswordReset,
  requestSignupPasscode,
  terminateSession,
  validateResetToken,
  type ReauthenticationWindow,
  type SessionContext,
  type SessionSummary,
} from '../api/authApi';

const SESSION_QUERY_KEY = ['auth', 'session'] as const;

export function useSessionContext(): UseQueryResult<SessionContext | null, Error> {
  return useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: async (): Promise<SessionContext | null> => {
      try {
        const context = await fetchSessionContext();

        rememberServerTime(context?.serverTime);
        return context;
      } catch (failure) {
        if (failure instanceof ApiError && failure.status === 401) {
          return null;
        }
        throw failure;
      }
    },
    retry: false,
  });
}

export interface SignupInput {
  email: string;
  passcode: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

export function useRequestSignupPasscode(): UseMutationResult<void, Error, string> {
  return useMutation({
    mutationFn: (email: string) => requestSignupPasscode(email),
  });
}

export function useCompleteSignup(): UseMutationResult<SessionContext, Error, SignupInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SignupInput) => completeSignup(input.email, input.passcode, input.password),
    onSuccess: (context) => queryClient.setQueryData(SESSION_QUERY_KEY, context),
  });
}

export function useLogin(): UseMutationResult<SessionContext, Error, LoginInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: LoginInput) => login(input.email, input.password),
    onSuccess: (context) => queryClient.setQueryData(SESSION_QUERY_KEY, context),
  });
}

export function useReauthenticate(): UseMutationResult<ReauthenticationWindow, Error, string> {
  return useMutation({
    mutationFn: (password: string) => reauthenticate(password),
  });
}

export function useChangePassword(): UseMutationResult<void, Error, string> {
  return useMutation({
    mutationFn: (newPassword: string) => changePassword(newPassword),
  });
}

const OWN_SESSIONS_QUERY_KEY = ['auth', 'sessions', 'own'] as const;

export function useOwnSessions(): UseQueryResult<SessionSummary[], Error> {
  return useQuery({
    queryKey: OWN_SESSIONS_QUERY_KEY,
    queryFn: fetchOwnSessions,
  });
}

export function useUserSessions(userId: string): UseQueryResult<SessionSummary[], Error> {
  return useQuery({
    queryKey: ['auth', 'sessions', 'user', userId],
    queryFn: () => fetchUserSessions(userId),
    enabled: userId !== '',
    retry: false,
  });
}

export function useTerminateSession(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reference: string) => terminateSession(reference),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['auth', 'sessions'] }),
  });
}

export function useLogout(): UseMutationResult<void, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => logout(),
    retry: false,
    onSuccess: () => queryClient.setQueryData(SESSION_QUERY_KEY, null),
  });
}

export function useRequestPasswordReset(): UseMutationResult<void, Error, string> {
  return useMutation({ mutationFn: requestPasswordReset, retry: false });
}

export function useResetToken(token: string): UseQueryResult<true, Error> {
  return useQuery({
    queryKey: ['auth', 'password-reset', token],
    queryFn: async () => {
      await validateResetToken(token);
      return true as const;
    },
    retry: false,
    enabled: token !== '',
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });
}

export interface CompleteResetInput {
  token: string;
  newPassword: string;
}

export function useCompletePasswordReset(): UseMutationResult<void, Error, CompleteResetInput> {
  return useMutation({
    mutationFn: ({ token, newPassword }: CompleteResetInput) =>
      completePasswordReset(token, newPassword),
    retry: false,
  });
}
