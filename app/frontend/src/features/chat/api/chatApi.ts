import { apiRequest } from '../../../shared/api/client';
import type { WorkDraft } from '../../../shared/model/work-draft';

export type RoomKind = 'DIRECT' | 'GROUP' | 'CHANNEL' | 'ANNOUNCEMENT';

export interface ConversationRow {
  id: string;
  kind: RoomKind;
  counterpartId: string | null;
  counterpartName: string | null;
  counterpartActive: boolean;

  name: string | null;
  lastMessagePreview: string | null;
  lastMessageDeleted: boolean;
  lastMessageAt: string | null;

  unreadCount: number;
}

export interface JoinableRoom {
  id: string;
  name: string;
}

export interface ConversationsPayload {
  conversations: ConversationRow[];

  joinable: JoinableRoom[];
  cursor: number;
}

interface ThreadEntryBase {
  id: string;

  authorId: string;

  authorName: string | null;
  sentAt: string;
  seq: number;
}

export interface SpokenRow extends ThreadEntryBase {
  kind: 'SPOKEN';
  body: string;
  editedAt: string | null;
  deletedAt: string | null;
  convertedTaskId: string | null;
}

export interface WorkMarkRow extends ThreadEntryBase {
  kind: 'WORK_MARK';
  work: { kind: 'TASK' | 'RUN'; id: string };
}

export type MessageRow = SpokenRow | WorkMarkRow;

export interface MessagesPayload {
  messages: MessageRow[];
  cursor: number;
  hasMore: boolean;
}

export function fetchConversations(): Promise<ConversationsPayload> {
  return apiRequest<ConversationsPayload>('/conversations');
}

export function startConversation(personId: string): Promise<ConversationRow> {
  return apiRequest<ConversationRow>('/conversations', {
    method: 'POST',
    body: JSON.stringify({ personId }),
  });
}

export function createGroup(name: string): Promise<ConversationRow> {
  return apiRequest<ConversationRow>('/conversations/group', {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export function joinRoom(conversationId: string): Promise<void> {
  return apiRequest<void>(`/conversations/${encodeURIComponent(conversationId)}/join`, {
    method: 'POST',
  });
}

export function leaveRoom(conversationId: string): Promise<void> {
  return apiRequest<void>(`/conversations/${encodeURIComponent(conversationId)}/leave`, {
    method: 'POST',
  });
}

export interface Participant {
  personId: string;
  displayName: string;
  active: boolean;
}

export function fetchParticipants(conversationId: string): Promise<Participant[]> {
  return apiRequest<Participant[]>(
    `/conversations/${encodeURIComponent(conversationId)}/participants`,
  );
}

export function addParticipant(conversationId: string, personId: string): Promise<void> {
  return apiRequest<void>(`/conversations/${encodeURIComponent(conversationId)}/participants`, {
    method: 'POST',
    body: JSON.stringify({ personId }),
  });
}

export function renameRoom(conversationId: string, name: string): Promise<void> {
  return apiRequest<void>(`/conversations/${encodeURIComponent(conversationId)}/name`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  });
}

export function fetchMessages(conversationId: string, before?: number): Promise<MessagesPayload> {
  const query = before === undefined ? '' : `&before=${before}`;
  return apiRequest<MessagesPayload>(`/conversations/${conversationId}/messages?limit=50${query}`);
}

export function sendMessage(conversationId: string, body: string): Promise<MessageRow> {
  return apiRequest<MessageRow>(`/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  });
}

export interface ConversionContext {
  suggestedAssigneeId: string | null;
  suggestedAssigneeName: string | null;
  suggestedAssigneeActive: boolean;
  title: string;
  description: string;

  instances: { id: string; name: string }[];
}

export interface ConversionDraft {
  title: string;
  description: string | null;
  assigneeId: string;

  deadline: string | null;
  priority: string;

  instanceId: string | null;
}

export function fetchConversionContext(
  conversationId: string,
  messageId: string,
): Promise<ConversionContext> {
  return apiRequest<ConversionContext>(
    `/conversations/${conversationId}/messages/${messageId}/conversion-context`,
  );
}

export function convertMessage(
  conversationId: string,
  messageId: string,
  draft: ConversionDraft,
): Promise<{ taskId: string }> {
  return apiRequest<{ taskId: string }>(
    `/conversations/${conversationId}/messages/${messageId}/convert`,
    { method: 'POST', body: JSON.stringify(draft) },
  );
}

export interface TaskOrigin {
  conversationId: string;
  messageId: string;
}

export function fetchTaskOrigin(taskId: string): Promise<TaskOrigin> {
  return apiRequest<TaskOrigin>(`/conversations/origin/${taskId}`);
}

export function markRead(conversationId: string, throughMessageId: string): Promise<void> {
  return apiRequest<void>(`/conversations/${conversationId}/read`, {
    method: 'POST',
    body: JSON.stringify({ throughMessageId }),
  });
}

export interface AssignmentContext {
  counterpartId: string | null;
  counterpartName: string | null;
  counterpartActive: boolean;

  mayAssignTask: boolean;

  mayStartRun: boolean;

  templates: { id: string; name: string; overview: string | null; stepCount: number }[];
}

export function fetchAssignmentContext(conversationId: string): Promise<AssignmentContext> {
  return apiRequest<AssignmentContext>(`/conversations/${conversationId}/assignment-context`);
}

export interface DirectTaskDraft {
  title: string;
  description: string | null;
  deadline: string | null;
  priority: string;
}

export function assignTaskInConversation(
  conversationId: string,
  draft: DirectTaskDraft,
): Promise<{ taskId: string }> {
  return apiRequest<{ taskId: string }>(`/conversations/${conversationId}/tasks`, {
    method: 'POST',
    body: JSON.stringify(draft),
  });
}

export function startRunInConversation(
  conversationId: string,
  templateId: string,
): Promise<{ instanceId: string }> {
  return apiRequest<{ instanceId: string }>(`/conversations/${conversationId}/runs`, {
    method: 'POST',
    body: JSON.stringify({ templateId }),
  });
}

export type MessageSelectionDraft = WorkDraft;

export function draftProcessFromMessages(
  conversationId: string,
  messageIds: readonly string[],
): Promise<MessageSelectionDraft> {
  return apiRequest<MessageSelectionDraft>(
    `/conversations/${encodeURIComponent(conversationId)}/message-selection/draft`,
    { method: 'POST', body: JSON.stringify({ messageIds }) },
  );
}

export interface SubmittedProcessStep {
  messageId: string;
  title: string;
  description: string;
  assigneeId: string;
  deadline: string | null;
}

export function startProcessFromMessages(
  conversationId: string,
  run: { name: string; steps: readonly SubmittedProcessStep[] },
): Promise<{ instanceId: string }> {
  return apiRequest<{ instanceId: string }>(
    `/conversations/${encodeURIComponent(conversationId)}/processes`,
    { method: 'POST', body: JSON.stringify(run) },
  );
}

export function appendMessagesToTemplate(
  conversationId: string,
  append: { templateId: string; steps: readonly SubmittedProcessStep[] },
): Promise<void> {
  return apiRequest<void>(`/conversations/${encodeURIComponent(conversationId)}/processes`, {
    method: 'POST',
    body: JSON.stringify(append),
  });
}
