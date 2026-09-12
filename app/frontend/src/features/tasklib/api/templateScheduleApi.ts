import { apiRequest } from '../../../shared/api/client';

export type ScheduleCadence = 'DAILY' | 'WEEKLY' | 'MONTHLY';

export interface TemplateSchedule {
  id: string;
  templateId: string;
  assigneeId: string;
  cadence: ScheduleCadence;

  dayOfWeek: number | null;

  dayOfMonth: number | null;
  active: boolean;
  nextOccurrence: string | null;
  timesRaised: number;
  lastRaisedOn: string | null;
  createdAt: string;
}

export interface ScheduleInput {
  assigneeId: string;
  cadence: ScheduleCadence;
  dayOfWeek?: number;
  dayOfMonth?: number;
}

export function fetchTemplateSchedules(templateId: string): Promise<TemplateSchedule[]> {
  return apiRequest<TemplateSchedule[]>(`/task-templates/${templateId}/schedules`);
}

export function setTemplateSchedule(input: {
  templateId: string;
  schedule: ScheduleInput;
}): Promise<TemplateSchedule> {
  return apiRequest<TemplateSchedule>(`/task-templates/${input.templateId}/schedules`, {
    method: 'POST',
    body: JSON.stringify(input.schedule),
  });
}

export function pauseTemplateSchedule(input: {
  templateId: string;
  scheduleId: string;
}): Promise<TemplateSchedule> {
  return apiRequest<TemplateSchedule>(
    `/task-templates/${input.templateId}/schedules/${input.scheduleId}/pause`,
    { method: 'POST' },
  );
}

export function resumeTemplateSchedule(input: {
  templateId: string;
  scheduleId: string;
}): Promise<TemplateSchedule> {
  return apiRequest<TemplateSchedule>(
    `/task-templates/${input.templateId}/schedules/${input.scheduleId}/resume`,
    { method: 'POST' },
  );
}
