import { apiRequest } from '../../../shared/api/client';
import type { WorkspaceUse } from '../model/setupDraft';
import type {
  InvitedRole,
  MembershipStatus,
  PendingInvitation,
  Person,
} from '../model/reportingTree';

export type { WorkspaceUse };

export interface SetupPrefill {
  setupCompleted: boolean;
  suggestedTimezone: string;
  availableTimezones: string[];
}

export interface Workspace {
  id: string;
  name: string;
  use: WorkspaceUse;
  timezone: string;
}

export interface WorkspaceSetup {
  workspace: Workspace;
  landingTarget: string;
}

export interface SetupInput {
  ownerName: string;
  workspaceName: string;
  use: WorkspaceUse;
  timezone: string;
}

export function fetchSetupPrefill(): Promise<SetupPrefill> {
  return apiRequest<SetupPrefill>('/workspace/setup');
}

export function setUpWorkspace(input: SetupInput): Promise<WorkspaceSetup> {
  return apiRequest<WorkspaceSetup>('/workspace/setup', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export type { InvitedRole, MembershipStatus, PendingInvitation, Person };

export interface People {
  people: Person[];
  invitations?: PendingInvitation[];
  onlyMember: boolean;
}

export interface InviteInput {
  emailAddress: string;
  role: InvitedRole;
  managerId: string;
}

export interface Invitation {
  id: string;
  emailAddress: string;
  role: InvitedRole;
  managerId: string;
  state: string;
  expiresAt: string;
}

export function fetchPeople(): Promise<People> {
  return apiRequest<People>('/workspace/people');
}

export function invitePerson(input: InviteInput): Promise<Invitation> {
  return apiRequest<Invitation>('/workspace/invitations', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export interface RevokedInvitation {
  id: string;
  emailAddress: string;
  state: string;

  revokedAt: string | null;
}

export function revokeInvitation(id: string): Promise<RevokedInvitation> {
  return apiRequest<RevokedInvitation>(`/workspace/invitations/${id}/revoke`, { method: 'POST' });
}

export interface MovingPerson {
  membershipId: string;
  displayName: string;
}

export interface ReassignPreview {
  personName: string;
  formerManagerName: string | null;
  newManagerName: string;
  movingWithThem: MovingPerson[];
  alreadyTheirManager: boolean;
}

export interface ReassignedReportingLine {
  membershipId: string;
  formerManagerId: string | null;
  newManagerId: string;

  changed: boolean;
}

export function fetchReassignPreview(
  membershipId: string,
  proposedManagerId: string,
): Promise<ReassignPreview> {
  return apiRequest<ReassignPreview>(
    `/workspace/people/${membershipId}/manager/preview?proposedManagerId=${proposedManagerId}`,
  );
}

export function reassignReportingLine(
  membershipId: string,
  proposedManagerId: string,
): Promise<ReassignedReportingLine> {
  return apiRequest<ReassignedReportingLine>(`/workspace/people/${membershipId}/manager`, {
    method: 'POST',
    body: JSON.stringify({ proposedManagerId }),
  });
}

export interface Deactivation {
  membershipId: string;

  changed: boolean;
  reportsMoved: string[];

  reportsMovedTo: string | null;
}

export function deactivatePerson(membershipId: string): Promise<Deactivation> {
  return apiRequest<Deactivation>(
    `/workspace/people/${encodeURIComponent(membershipId)}/deactivate`,
    { method: 'POST' },
  );
}

export interface ErasurePreview {
  membershipId: string;

  displayName: string | null;
  deactivatedAt: string | null;
  eligible: boolean;

  refusal: string | null;
  destroys: string[];
  survives: string[];
  alreadyErased: boolean;
}

export interface Erasure {
  membershipId: string;

  opaqueIdentifier: string;

  changed: boolean;
}

export function fetchErasurePreview(membershipId: string): Promise<ErasurePreview> {
  return apiRequest<ErasurePreview>(
    `/workspace/people/${encodeURIComponent(membershipId)}/erasure`,
  );
}

export function erasePerson(membershipId: string, typedName: string): Promise<Erasure> {
  return apiRequest<Erasure>(`/workspace/people/${encodeURIComponent(membershipId)}/erase`, {
    method: 'POST',
    body: JSON.stringify({ typedName }),
  });
}

export interface OwnSession {
  reference: string;
  deviceSummary: string | null;
  coarseLocation: string | null;
  createdAt: string;
}

export interface OwnAccount {
  personId: string;
  emailAddress: string;
  displayName: string | null;
  role: string;
  accountState: string;
  createdAt: string;
  sessions: OwnSession[];
}

export interface ReportingPeriod {
  managerName: string | null;
  from: string;

  until: string | null;
}

export interface OwnData {
  account: OwnAccount;
  membership: {
    membershipId: string;
    status: MembershipStatus;
    deactivatedAt: string | null;
    managerName: string | null;
  };
  reportingLineHistory: ReportingPeriod[];
  consent: { version: string; language: string; agreedAt: string } | null;
  authored: { tasks: number; comments: number; approvals: number };
  exports: { produced: number; limit: number };
  producedForSomebodyElse: boolean;
}

export function fetchOwnData(): Promise<OwnData> {
  return apiRequest<OwnData>('/workspace/me');
}

export function exportOwnData(): Promise<OwnData> {
  return apiRequest<OwnData>('/workspace/me/export', { method: 'POST' });
}

export interface ProfileChange {
  displayName: string;

  changed: boolean;
}

export function editOwnProfile(displayName: string): Promise<ProfileChange> {
  return apiRequest<ProfileChange>('/workspace/me/profile', {
    method: 'PUT',
    body: JSON.stringify({ displayName }),
  });
}

export interface WorkspaceSettings {
  name: string | null;
  use: string | null;
  timezone: string;

  workingDays: string[];
  workingHoursStart: string;
  workingHoursEnd: string;
  atRiskWindowHours: number;

  escalationIntervalsHours: number[];
  quietHoursStart: string;

  quietHoursEnd: string;
  invitationApprovalRequired: boolean;

  closureCoverageThresholdPercent: number;

  templateIdleWindowDays: number;
  effectiveFrom: string;

  changedFields: string[];
}

export type WorkspaceSettingsInput = Omit<WorkspaceSettings, 'effectiveFrom' | 'changedFields'>;

export function fetchWorkspaceSettings(): Promise<WorkspaceSettings> {
  return apiRequest<WorkspaceSettings>('/workspace/settings');
}

export function updateWorkspaceSettings(input: WorkspaceSettingsInput): Promise<WorkspaceSettings> {
  return apiRequest<WorkspaceSettings>('/workspace/settings', {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}
