import { apiRequest } from '../../../shared/api/client';

export type TemplateStatus = 'DRAFT' | 'PROPOSED' | 'APPROVED' | 'RETIRED';
export type TemplateSort = 'MOST_USED' | 'NEWEST' | 'ALPHABETICAL';
export type TemplatePriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export interface TaskTemplate {
  id: string;
  title: string;
  description: string | null;
  type: string | null;
  priority: TemplatePriority;
  estimatedHours: number | null;
  checklist: string[];
  status: TemplateStatus;
  authorId: string;

  rejectionReason: string | null;

  convertedToProcessTemplateId: string | null;
  processShapeHints: ProcessShapeHint[];

  metadata: TemplateMetadata;

  timesUsed: number;
  createdAt: string;
  updatedAt: string;

  discoveredByPipeline: boolean;
}

export type MetadataField =
  | 'RESPONSIBLE_ROLE'
  | 'TRIGGER_NOTE'
  | 'REQUIRED_INPUT'
  | 'EXPECTED_OUTPUT'
  | 'OUTPUT_KIND'
  | 'COMPLETION_CRITERIA';

export type OutputKind =
  'TEXT' | 'DESIGN' | 'REPORT' | 'SCHEDULE' | 'DECISION' | 'PHYSICAL' | 'NONE';

export interface TemplateMetadata {
  responsibleRole: string | null;
  triggerNote: string | null;
  requiredInput: string | null;
  expectedOutput: string | null;
  outputKind: OutputKind | null;
  completionCriteria: string | null;

  nextAsk: MetadataField | null;
  missing: MetadataField[];
}

export interface TemplateLibrary {
  templates: TaskTemplate[];

  types: string[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface TemplateQuery {
  q?: string;
  type?: string;
  status?: TemplateStatus;
  mine?: boolean;
  sort?: TemplateSort;
  page?: number;
  size?: number;
}

export interface StampTaskInput {
  title?: string;
  description?: string;
  assigneeId: string;
  deadline?: string;
  priority?: TemplatePriority;
}

export interface StampedTask {
  taskId: string;
  title: string;
  templateId: string;
}

export interface ProcessShapeHint {
  signal: 'handover-language' | 'checklist-length';

  evidence: string;
}

export interface TemplateDraft {
  title: string;
  description?: string;
  type?: string;
  priority?: TemplatePriority;
  estimatedHours?: number | null;
  checklist?: string[];
  submitForApproval: boolean;
}

function queryOf(query: TemplateQuery): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && `${value}`.trim() !== '' && value !== false) {
      params.set(key, `${value}`);
    }
  }
  const encoded = params.toString();
  return encoded === '' ? '' : `?${encoded}`;
}

export function fetchTemplateLibrary(query: TemplateQuery): Promise<TemplateLibrary> {
  return apiRequest<TemplateLibrary>(`/task-templates${queryOf(query)}`);
}

export function fetchTemplate(id: string): Promise<TaskTemplate> {
  return apiRequest<TaskTemplate>(`/task-templates/${id}`);
}

export function fetchApprovalQueue(): Promise<TaskTemplate[]> {
  return apiRequest<TaskTemplate[]>('/task-templates/approval-queue');
}

export interface DraftCandidate {
  title: string;
  variants: string[];
  drafts: number;
  firstSeen: string;
  lastSeen: string;
  templateIds: string[];
}

export function fetchDraftCandidates(): Promise<DraftCandidate[]> {
  return apiRequest<DraftCandidate[]>('/task-templates/draft-candidates');
}

export function createTemplate(draft: TemplateDraft): Promise<TaskTemplate> {
  return apiRequest<TaskTemplate>('/task-templates', {
    method: 'POST',
    body: JSON.stringify(draft),
  });
}

export function editTemplate(input: { id: string; draft: TemplateDraft }): Promise<TaskTemplate> {
  return apiRequest<TaskTemplate>(`/task-templates/${input.id}`, {
    method: 'PATCH',
    body: JSON.stringify(input.draft),
  });
}

export function recordTemplateMetadata(input: {
  id: string;
  field: MetadataField;
  answer: string;
}): Promise<TaskTemplate> {
  return apiRequest<TaskTemplate>(`/task-templates/${input.id}/metadata`, {
    method: 'PATCH',
    body: JSON.stringify({ field: input.field, answer: input.answer }),
  });
}

export function approveTemplate(input: {
  id: string;
  edited?: TemplateDraft;
}): Promise<TaskTemplate> {
  return apiRequest<TaskTemplate>(`/task-templates/${input.id}/approval`, {
    method: 'POST',
    body: input.edited === undefined ? undefined : JSON.stringify(input.edited),
  });
}

export function approveTemplates(templateIds: string[]): Promise<TaskTemplate[]> {
  return apiRequest<TaskTemplate[]>('/task-templates/approvals', {
    method: 'POST',
    body: JSON.stringify({ templateIds }),
  });
}

export function sendTemplateBack(input: { id: string; reason: string }): Promise<TaskTemplate> {
  return apiRequest<TaskTemplate>(`/task-templates/${input.id}/rejection`, {
    method: 'POST',
    body: JSON.stringify({ reason: input.reason }),
  });
}

export function checkTemplateShape(draft: {
  title: string;
  description?: string;
  checklist: string[];
}): Promise<ProcessShapeHint[]> {
  return apiRequest<ProcessShapeHint[]>('/task-templates/shape-check', {
    method: 'POST',
    body: JSON.stringify(draft),
  });
}

export interface TemplateSuggestion {
  id: string;
  title: string;
  timesUsed: number;

  similarity: number;
}

export function fetchTemplateSuggestions(title: string, limit = 3): Promise<TemplateSuggestion[]> {
  return apiRequest<TemplateSuggestion[]>(
    `/task-templates/suggestions?title=${encodeURIComponent(title)}&limit=${limit}`,
  );
}

export interface LiveWork {
  notStarted: number;
  running: number;
  blocked: number;
  inReview: number;
  finished: number;
  overdue: number;
}

export interface TemplateUsage {
  stamped: number;
  estimatedHours: number | null;
  medianActiveSeconds: number | null;
  lowerQuartileSeconds: number | null;
  upperQuartileSeconds: number | null;
  measuredTasks: number;
  tooFewToAverage: boolean;
  varyWidely: boolean;
  passedFirstTime: number;
  reviewed: number;
  live: LiveWork;
}

export function fetchTemplateUsage(id: string): Promise<TemplateUsage> {
  return apiRequest<TemplateUsage>(`/task-templates/${id}/usage`);
}

export function retireTemplate(id: string): Promise<TaskTemplate> {
  return apiRequest<TaskTemplate>(`/task-templates/${id}/retirement`, { method: 'POST' });
}

export function copyTemplate(id: string): Promise<TaskTemplate> {
  return apiRequest<TaskTemplate>(`/task-templates/${id}/copy`, { method: 'POST' });
}

export function stampTaskFromTemplate(input: {
  id: string;
  task: StampTaskInput;
}): Promise<StampedTask> {
  return apiRequest<StampedTask>(`/task-templates/${input.id}/tasks`, {
    method: 'POST',
    body: JSON.stringify(input.task),
  });
}
