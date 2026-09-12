import { apiRequest } from '../../../shared/api/client';
import type { TaskState } from '../../../shared/ui/TaskStateChip';

export type { TaskState };

export type PhaseKind = 'WAIT' | 'ACTIVE' | 'BLOCKED' | 'REVIEW' | 'APPROVAL';

export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

import type { TaskLifecycleState } from '../../../shared/ui/TaskActionRail';

export type ApiTaskState = TaskLifecycleState;

export interface TaskSummary {
  id: string;
  title: string;
  assigneeId: string;

  assigneeName: string;
  deadline: string | null;
  priority: TaskPriority;
  state: ApiTaskState;

  openPhase: PhaseKind | null;
  phaseSince: string | null;

  mine: boolean;

  directedByMe: boolean;

  deadlineProposalOpen: boolean;

  atRisk: boolean;

  kind: 'TASK' | 'TICKET';

  templateId: string | null;

  categoryId: string | null;

  categoryName: string | null;
}

export interface Task {
  id: string;
  title: string;
  description: string | null;

  assigneeId: string | null;
  assigneeName: string;
  creatorId: string;
  deadline: string | null;
  priority: TaskPriority;
  state: ApiTaskState;
  selfAssigned: boolean;
  atRisk: boolean;
  createdAt: string;
}

export interface CreateTaskInput {
  templateId?: string;

  kind?: 'TASK' | 'TICKET';
  title: string;
  description: string;
  assigneeId: string;

  deadline: string | null;
  priority: TaskPriority;
}

const CHIP_STATE: Record<ApiTaskState, TaskState> = {
  CREATED: 'Created',
  ACCEPTED: 'Accepted',
  IN_PROGRESS: 'InProgress',
  BLOCKED: 'Blocked',
  COMPLETED: 'Completed',
  APPROVED: 'Approved',
  CLOSED: 'Closed',
};

export function chipState(state: ApiTaskState): TaskState {
  return CHIP_STATE[state];
}

export function fetchTasks(): Promise<{ tasks: TaskSummary[] }> {
  return apiRequest<{ tasks: TaskSummary[] }>('/tasks');
}

export type TaskSectionKey =
  'needs-you' | 'overdue' | 'due-soon' | 'in-progress' | 'waiting' | 'not-started' | 'archive';

export type TaskSortKey = 'deadline' | 'priority' | 'recent';

export interface TaskQueryFilter {
  q?: string;
  assignee?: string;
  priority?: TaskSummary['priority'];
  from?: string;
  to?: string;

  category?: string;
}

export interface TaskSectionCount {
  section: TaskSectionKey;
  count: number;
}

export interface TaskSections {
  sections: TaskSectionCount[];
  total: number;
}

export interface PagedTasks {
  section: TaskSectionKey;
  rows: TaskSummary[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

function queryOf(filter: TaskQueryFilter, extra: Record<string, string | number> = {}): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries({ ...filter, ...extra })) {
    if (value !== undefined && value !== null && `${value}`.trim() !== '') {
      params.set(key, `${value}`);
    }
  }
  const query = params.toString();
  return query === '' ? '' : `?${query}`;
}

export function fetchTaskSections(filter: TaskQueryFilter): Promise<TaskSections> {
  return apiRequest<TaskSections>(`/tasks/sections${queryOf(filter)}`);
}

export function fetchTaskSection(input: {
  section: TaskSectionKey;
  page: number;
  size: number;
  sort: TaskSortKey;
  filter: TaskQueryFilter;
}): Promise<PagedTasks> {
  return apiRequest<PagedTasks>(
    `/tasks${queryOf(input.filter, {
      section: input.section,
      page: input.page,
      size: input.size,
      sort: input.sort,
    })}`,
  );
}

export function createTask(input: CreateTaskInput): Promise<Task> {
  return apiRequest<Task>('/tasks', { method: 'POST', body: JSON.stringify(input) });
}

export function acceptTask(id: string): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/accept`, { method: 'POST' });
}

export function startTask(id: string): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/start`, { method: 'POST' });
}

export interface BlockTaskInput {
  id: string;
  reason: string;
}

export function blockTask({ id, reason }: BlockTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/block`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export interface UnblockTaskInput {
  id: string;
  resolution: string;
}

export function unblockTask({ id, resolution }: UnblockTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/unblock`, {
    method: 'POST',
    body: JSON.stringify({ resolution: resolution === '' ? null : resolution }),
  });
}

export interface CompleteTaskInput {
  id: string;
  note: string;
  externalLink: string;
}

export function completeTask({ id, note, externalLink }: CompleteTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/complete`, {
    method: 'POST',
    body: JSON.stringify({ note, externalLink: externalLink === '' ? null : externalLink }),
  });
}

export interface AssignablePerson {
  id: string;
  displayName: string;
}

export function fetchAssignablePeople(): Promise<{ people: AssignablePerson[] }> {
  return apiRequest<{ people: AssignablePerson[] }>('/tasks/assignable-people');
}

export interface PhaseSpan {
  kind: PhaseKind;
  seconds: number;
}

export interface CompletionProof {
  note: string;
  externalLink: string | null;
  submittedAt: string;
}

export interface Approval {
  score: number;
  comment: string | null;
  reviewerId: string;
  decidedAt: string;
}

export interface TaskDetail extends Task {
  templateId: string | null;

  openPhase: PhaseKind | null;
  phaseSince: string | null;
  phases: PhaseSpan[];
  proof: CompletionProof | null;
  approval: Approval | null;
  completedAt: string | null;

  deadlineMet: boolean | null;
  atRisk: boolean;

  deadlineProposal: DeadlineProposal | null;
}

export interface DeadlineProposal {
  proposedDeadline: string;

  reason: string;
  proposerId: string;
  proposedAt: string;
}

export function fetchReviewQueue(): Promise<{ tasks: TaskSummary[] }> {
  return apiRequest<{ tasks: TaskSummary[] }>('/tasks/review-queue');
}

export function fetchTaskDetail(id: string): Promise<TaskDetail> {
  return apiRequest<TaskDetail>(`/tasks/${id}`);
}

export interface ApproveTaskInput {
  id: string;
  score: number;
  comment: string;
}

export function approveTask({ id, score, comment }: ApproveTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify({ score, comment: comment === '' ? null : comment }),
  });
}

export interface ReturnTaskInput {
  id: string;
  reason: string;
}

export function returnTaskForRework({ id, reason }: ReturnTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/return`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function closeTask(id: string): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/close`, { method: 'POST' });
}

export interface RejectTaskInput {
  id: string;
  reason: string;
}

export function rejectTask({ id, reason }: RejectTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export interface ProposeDeadlineInput {
  id: string;
  proposedDeadline: string;
  reason: string;
}

export interface SetDeadlineInput {
  id: string;
  deadline: string;
}

export function setDeadline({ id, deadline }: SetDeadlineInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/deadline`, {
    method: 'POST',
    body: JSON.stringify({ deadline }),
  });
}

export interface DeadlineNotice {
  taskId: string;
  taskTitle: string;
  assigneeId: string;
  assigneeName: string;
  deadline: string;
  setAt: string;
}

export function fetchDeadlineNotices(): Promise<{ notices: DeadlineNotice[] }> {
  return apiRequest<{ notices: DeadlineNotice[] }>('/tasks/deadline-notices');
}

export function acknowledgeDeadlineNotice(id: string): Promise<void> {
  return apiRequest<void>(`/tasks/${id}/deadline-notice/acknowledge`, { method: 'POST' });
}

export type LinkRole = 'INPUT' | 'OUTPUT' | 'REFERENCE';

export interface TaskLink {
  id: string;
  url: string;
  label: string | null;

  displayText: string;
  role: LinkRole;
  addedById: string;
  addedAt: string;
}

export interface ChecklistItem {
  id: string;
  position: number;
  text: string;
  done: boolean;
  doneAt: string | null;
  authoredById: string;
}

export interface TaskMaterial {
  links: TaskLink[];
  checklist: ChecklistItem[];
}

export function fetchTaskMaterial(id: string): Promise<TaskMaterial> {
  return apiRequest<TaskMaterial>(`/tasks/${id}/material`);
}

export function attachLink(input: {
  id: string;
  url: string;
  label: string;
  role: LinkRole;
}): Promise<TaskLink> {
  return apiRequest<TaskLink>(`/tasks/${input.id}/links`, {
    method: 'POST',
    body: JSON.stringify({
      url: input.url,
      label: input.label === '' ? null : input.label,
      role: input.role,
    }),
  });
}

export function detachLink(input: { id: string; linkId: string }): Promise<void> {
  return apiRequest<void>(`/tasks/${input.id}/links/${input.linkId}`, { method: 'DELETE' });
}

export function addChecklistItem(input: { id: string; text: string }): Promise<ChecklistItem> {
  return apiRequest<ChecklistItem>(`/tasks/${input.id}/checklist`, {
    method: 'POST',
    body: JSON.stringify({ text: input.text }),
  });
}

export function tickChecklistItem(input: {
  id: string;
  itemId: string;
  done: boolean;
}): Promise<ChecklistItem> {
  return apiRequest<ChecklistItem>(`/tasks/${input.id}/checklist/${input.itemId}`, {
    method: 'POST',
    body: JSON.stringify({ done: input.done }),
  });
}

export function removeChecklistItem(input: { id: string; itemId: string }): Promise<void> {
  return apiRequest<void>(`/tasks/${input.id}/checklist/${input.itemId}`, { method: 'DELETE' });
}

export function proposeDeadline({
  id,
  proposedDeadline,
  reason,
}: ProposeDeadlineInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/deadline-proposals`, {
    method: 'POST',
    body: JSON.stringify({ proposedDeadline, reason }),
  });
}

export interface DecideDeadlineInput {
  id: string;
  accept: boolean;
  reason: string;
}

export function decideDeadline({ id, accept, reason }: DecideDeadlineInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/deadline-proposals/decision`, {
    method: 'POST',
    body: JSON.stringify({ accept, reason: reason === '' ? null : reason }),
  });
}

export interface EditTaskInput {
  id: string;

  deadline: string;
  priority: TaskPriority;
  description: string;
}

export function editTask({ id, deadline, priority, description }: EditTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}`, {
    method: 'PUT',
    body: JSON.stringify({
      deadline,
      priority,
      description: description === '' ? null : description,
    }),
  });
}

export interface ActivityEntry {
  kind: 'TRANSITION' | 'COMMENT';
  occurredAt: string;
  actorId: string;

  actorName: string;
  from: ApiTaskState | null;
  to: ApiTaskState | null;
  reason: string | null;

  overridden: boolean;
  body: string | null;
}

export function fetchTaskActivity(id: string): Promise<{ entries: ActivityEntry[] }> {
  return apiRequest<{ entries: ActivityEntry[] }>(`/tasks/${id}/activity`);
}

export interface CommentOnTaskInput {
  id: string;
  body: string;
}

export function commentOnTask({ id, body }: CommentOnTaskInput): Promise<unknown> {
  return apiRequest<unknown>(`/tasks/${id}/comments`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  });
}

export interface ReassignTaskInput {
  id: string;
  newAssigneeId: string;
  reason: string;
}

export function reassignTask({ id, newAssigneeId, reason }: ReassignTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/reassign`, {
    method: 'POST',
    body: JSON.stringify({ newAssigneeId, reason }),
  });
}

export interface OverrideTaskInput {
  id: string;
  targetState: ApiTaskState;
  reason: string;
}

export function overrideTask({ id, targetState, reason }: OverrideTaskInput): Promise<Task> {
  return apiRequest<Task>(`/tasks/${id}/override`, {
    method: 'POST',
    body: JSON.stringify({ targetState, reason }),
  });
}

export interface TemplateTaskRow {
  taskId: string;
  title: string;
  assigneeId: string | null;

  assigneeName: string | null;
  state: ApiTaskState;
  deadline: string | null;
}

export function fetchTemplateTasks(input: {
  templateId: string;
  band: string;
}): Promise<TemplateTaskRow[]> {
  return apiRequest<TemplateTaskRow[]>(
    `/task-templates/${input.templateId}/tasks?band=${encodeURIComponent(input.band)}`,
  );
}

export interface ThroughputWeek {
  starting: string;
  created: number;
  closed: number;
}

export function fetchThroughput(weeks: number): Promise<ThroughputWeek[]> {
  return apiRequest<ThroughputWeek[]>(`/tasks/throughput?weeks=${weeks}`);
}
