import { apiRequest } from '../../../shared/api/client';

export type AccountState = 'INVITED' | 'ACTIVE' | 'DEACTIVATED';
export type LandingTarget = 'TRIAGE' | 'MY_WORK' | 'WORKSPACE_SETUP';

export interface SessionContext {
  userId: string;
  email: string;
  accountState: AccountState;
  permissions: string[];
  landingTarget: LandingTarget;

  serverTime: string;
}

export function requestSignupPasscode(email: string): Promise<void> {
  return apiRequest<void>('/auth/signup/passcode', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export function completeSignup(
  email: string,
  passcode: string,
  password: string,
): Promise<SessionContext> {
  return apiRequest<SessionContext>('/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ email, passcode, password }),
  });
}

export function login(email: string, password: string): Promise<SessionContext> {
  return apiRequest<SessionContext>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function logout(): Promise<void> {
  return apiRequest<void>('/auth/logout', { method: 'POST' });
}

export function fetchSessionContext(): Promise<SessionContext> {
  return apiRequest<SessionContext>('/auth/session');
}

export interface ReauthenticationWindow {
  reauthenticatedUntil: string;
}

export function reauthenticate(password: string): Promise<ReauthenticationWindow> {
  return apiRequest<ReauthenticationWindow>('/auth/reauthenticate', {
    method: 'POST',
    body: JSON.stringify({ password }),
  });
}

export function changePassword(newPassword: string): Promise<void> {
  return apiRequest<void>('/auth/password', {
    method: 'POST',
    body: JSON.stringify({ newPassword }),
  });
}

export interface SessionSummary {
  reference: string;
  current: boolean;
  createdAt: string;
  lastActiveAt: string;
  ipAddress: string;
  deviceSummary: string;
  coarseLocation: string;
}

export function fetchOwnSessions(): Promise<SessionSummary[]> {
  return apiRequest<SessionSummary[]>('/auth/sessions');
}

export function fetchUserSessions(userId: string): Promise<SessionSummary[]> {
  return apiRequest<SessionSummary[]>(`/auth/users/${encodeURIComponent(userId)}/sessions`);
}

export function terminateSession(reference: string): Promise<void> {
  return apiRequest<void>(`/auth/sessions/${encodeURIComponent(reference)}`, {
    method: 'DELETE',
  });
}

export function requestPasswordReset(email: string): Promise<void> {
  return apiRequest<void>('/auth/password-reset/request', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export function validateResetToken(token: string): Promise<void> {
  return apiRequest<void>(`/auth/password-reset/${encodeURIComponent(token)}`);
}

export function completePasswordReset(token: string, newPassword: string): Promise<void> {
  return apiRequest<void>('/auth/password-reset/complete', {
    method: 'POST',
    body: JSON.stringify({ token, newPassword }),
  });
}
