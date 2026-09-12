import { useMutation, type UseMutationResult } from '@tanstack/react-query';

import {
  acceptInvitation,
  declineInvitation,
  type AcceptInvitationBody,
  type JoinedSession,
} from '../api/invitationApi';

export function useAcceptInvitation(
  token: string,
): UseMutationResult<JoinedSession, Error, AcceptInvitationBody> {
  return useMutation({
    mutationFn: (body: AcceptInvitationBody) => acceptInvitation(token, body),
    retry: false,
  });
}

export function useDeclineInvitation(token: string): UseMutationResult<void, Error, void> {
  return useMutation({
    mutationFn: () => declineInvitation(token),
    retry: false,
  });
}
