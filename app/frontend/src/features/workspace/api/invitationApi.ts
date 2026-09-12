import { apiRequest } from '../../../shared/api/client';

export interface InvitationPreview {
  workspaceName: string;
  role: string;
  manager: { displayName: string; reassigned: boolean };
  inviter: { displayName: string };
  consent: { version: string; text: string };
}

export function fetchInvitation(token: string): Promise<InvitationPreview> {
  return apiRequest<InvitationPreview>(`/workspace/invitations/${encodeURIComponent(token)}`);
}

export interface AcceptInvitationBody {
  displayName: string;
  password: string;

  consentAccepted: boolean;
  consentVersion: string;
}

export interface JoinedSession {
  userId: string;
  email: string;
  accountState: string;
  permissions: string[];
  landingTarget: string;
}

export function acceptInvitation(
  token: string,
  body: AcceptInvitationBody,
): Promise<JoinedSession> {
  return apiRequest<JoinedSession>(`/workspace/invitations/${encodeURIComponent(token)}/accept`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function declineInvitation(token: string): Promise<void> {
  return apiRequest<void>(`/workspace/invitations/${encodeURIComponent(token)}/decline`, {
    method: 'POST',
  });
}
