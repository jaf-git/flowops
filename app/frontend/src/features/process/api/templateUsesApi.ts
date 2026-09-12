import { apiRequest } from '../../../shared/api/client';

export interface PlannedIn {
  templateId: string;
  name: string;
  position: number;
  active: boolean;
}

export interface RunUsingTemplate {
  instanceId: string;
  instanceName: string;
  instanceState: 'RUNNING' | 'COMPLETE' | 'ABANDONED';
  stepId: string;
  stepCondition: 'PENDING' | 'REACHABLE' | 'ASSIGNED' | 'CLOSED';
  taskId: string | null;
  startedAt: string;
}

export interface TemplateUses {
  processTemplates: PlannedIn[];
  runs: RunUsingTemplate[];
  runsTotal: number;
}

export function fetchTemplateUses(taskTemplateId: string): Promise<TemplateUses> {
  return apiRequest<TemplateUses>(`/process-templates/using/${taskTemplateId}`);
}
