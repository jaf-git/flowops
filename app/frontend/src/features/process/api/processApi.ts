import { apiRequest } from '../../../shared/api/client';

export type StepCondition = 'PENDING' | 'REACHABLE' | 'ASSIGNED' | 'CLOSED';

export type InstanceState = 'RUNNING' | 'COMPLETE' | 'ABANDONED';

export interface TemplateStep {
  id: string;
  taskTemplateId: string;
  title: string | null;
  description: string | null;
  expectedDurationHours: number | null;
  position: number;
}

export interface Dependency {
  dependentStepId: string;
  dependsOnStepId: string;
}

export interface Template {
  id: string;
  name: string;
  overview: string | null;
  active: boolean;
  authorId: string;
  createdAt: string;
  steps: TemplateStep[];
  dependencies: Dependency[];
}

export interface TemplateSummary {
  id: string;
  name: string;
  overview: string | null;
  stepCount: number;
  active: boolean;
}

export interface InstanceStep {
  id: string;
  definitionId: string;

  title: string;
  description: string | null;
  expectedDurationHours: number | null;
  position: number;
  condition: StepCondition;
  taskId: string | null;

  planned: boolean;

  optional: boolean;

  conditionNote: string | null;

  skipped: boolean;
  dependsOn: string[];

  taskState: string | null;

  blockedReason: string | null;

  assigneeId: string | null;

  assigneeName: string | null;
  deadline: string | null;

  atRisk: boolean;
}

export interface Bottleneck {
  stepId: string;
  waitedMinutes: number;
}

export interface Instance {
  id: string;
  name: string;
  state: InstanceState;

  templateId: string | null;

  templateName: string | null;
  processOwnerId: string;
  startedAt: string;
  completedAt: string | null;
  progress: { closed: number; total: number };
  steps: InstanceStep[];

  awaitingAssignment: string[];
  bottleneck: Bottleneck | null;
  totalDurationMinutes: number | null;

  abandonedAt: string | null;

  abandonedReason: string | null;

  needingAttention: string[];
}

export interface InstanceSummary {
  id: string;
  name: string;

  templateName: string | null;
  state: InstanceState;
  progress: { closed: number; total: number };
  awaitingAssignmentCount: number;
}

export interface NewTemplateStep {
  id?: string;
  taskTemplateId: string;
  expectedDurationHours: number | null;

  optional?: boolean;

  conditionNote?: string | null;
}

export function fetchTemplates(): Promise<{ templates: TemplateSummary[] }> {
  return apiRequest<{ templates: TemplateSummary[] }>('/process-templates');
}

export function fetchTemplate(id: string): Promise<Template> {
  return apiRequest<Template>(`/process-templates/${id}`);
}

export function authorTemplate(input: {
  name: string;
  overview: string | null;
  steps: NewTemplateStep[];
}): Promise<Template> {
  return apiRequest<Template>('/process-templates', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function editTemplate(
  id: string,
  input: { overview: string | null; steps: NewTemplateStep[] },
): Promise<Template> {
  return apiRequest<Template>(`/process-templates/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function drawDependency(id: string, edge: Dependency): Promise<Template> {
  return apiRequest<Template>(`/process-templates/${id}/dependencies`, {
    method: 'POST',
    body: JSON.stringify(edge),
  });
}

export function eraseDependency(id: string, edge: Dependency): Promise<Template> {
  return apiRequest<Template>(
    `/process-templates/${id}/dependencies/${edge.dependentStepId}/${edge.dependsOnStepId}`,
    { method: 'DELETE' },
  );
}

export type InstancePopulation = 'ON_THE_BOARD' | 'EVERY_RUN';

export function fetchInstances(
  population: InstancePopulation,
): Promise<{ instances: InstanceSummary[] }> {
  return apiRequest<{ instances: InstanceSummary[] }>(
    `/process-instances?population=${population}`,
  );
}

export function fetchInstance(id: string): Promise<Instance> {
  return apiRequest<Instance>(`/process-instances/${id}`);
}

export function startInstance(input: {
  templateId: string;
  name: string;
  processOwnerId: string;
}): Promise<Instance> {
  return apiRequest<Instance>('/process-instances', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function startInstanceFromTasks(input: {
  name: string;
  processOwnerId: string;
  taskIds: string[];
}): Promise<Instance> {
  return apiRequest<Instance>('/process-instances/from-tasks', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export interface Candidate {
  id: string;
  displayName: string;
}

export function fetchAssignablePeople(instanceId: string): Promise<{ people: Candidate[] }> {
  return apiRequest<{ people: Candidate[] }>(`/process-instances/${instanceId}/assignable-people`);
}

export function skipStep(instanceId: string, stepId: string): Promise<Instance> {
  return apiRequest<Instance>(`/process-instances/${instanceId}/steps/${stepId}/skip`, {
    method: 'POST',
  });
}

export function assignStep(
  instanceId: string,
  stepId: string,

  input: { assigneeId: string; deadline: string | null },
): Promise<Instance> {
  return apiRequest<Instance>(`/process-instances/${instanceId}/steps/${stepId}/assignment`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export interface AttachableTask {
  id: string;
  title: string;
  state: string;
  assigneeId: string | null;
  deadline: string | null;
}

export interface NewProcessTask {
  title: string;
  description: string | null;
  assigneeId: string;
  deadline: string | null;
  priority: string;
}

export function fetchAttachableTasks(instanceId: string): Promise<{ tasks: AttachableTask[] }> {
  return apiRequest<{ tasks: AttachableTask[] }>(
    `/process-instances/${instanceId}/attachable-tasks`,
  );
}

export function fetchTasksForANewProcess(): Promise<{ tasks: AttachableTask[] }> {
  return apiRequest<{ tasks: AttachableTask[] }>('/process-instances/attachable-tasks');
}

export function addTaskToInstance(
  instanceId: string,
  input: { taskId?: string; newTask?: NewProcessTask; dependsOnStepIds: string[] },
): Promise<Instance> {
  return apiRequest<Instance>(`/process-instances/${instanceId}/tasks`, {
    method: 'POST',
    body: JSON.stringify({
      taskId: input.taskId ?? null,
      ...(input.newTask ?? {}),
      dependsOnStepIds: input.dependsOnStepIds,
    }),
  });
}

export function removeTaskFromInstance(instanceId: string, stepId: string): Promise<Instance> {
  return apiRequest<Instance>(`/process-instances/${instanceId}/tasks/${stepId}`, {
    method: 'DELETE',
  });
}

export function reorderInstanceTasks(instanceId: string, stepIds: string[]): Promise<Instance> {
  return apiRequest<Instance>(`/process-instances/${instanceId}/tasks/order`, {
    method: 'PATCH',
    body: JSON.stringify({ stepIds }),
  });
}

export function drawInstanceDependency(instanceId: string, edge: Dependency): Promise<Instance> {
  return apiRequest<Instance>(`/process-instances/${instanceId}/dependencies`, {
    method: 'POST',
    body: JSON.stringify(edge),
  });
}

export function eraseInstanceDependency(instanceId: string, edge: Dependency): Promise<Instance> {
  return apiRequest<Instance>(
    `/process-instances/${instanceId}/dependencies/${edge.dependentStepId}/${edge.dependsOnStepId}`,
    { method: 'DELETE' },
  );
}

export function stepsWaitingOn(instance: Instance, step: InstanceStep): InstanceStep[] {
  return instance.steps.filter((each) => each.dependsOn.includes(step.id));
}

export function waitsFor(instance: Instance, step: InstanceStep): InstanceStep[] {
  return step.dependsOn
    .map((id) => instance.steps.find((each) => each.id === id))
    .filter((each): each is InstanceStep => each !== undefined);
}

export function retireTemplate(id: string): Promise<Template> {
  return apiRequest<Template>(`/process-templates/${id}/retirement`, { method: 'POST' });
}

export function abandonInstance(id: string, reason: string): Promise<Instance> {
  return apiRequest<Instance>(`/process-instances/${id}/abandonment`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}
