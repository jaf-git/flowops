import { useState } from 'react';

import type { TaskTemplate, TemplateDraft, TemplatePriority } from '../api/taskTemplateApi';

export interface TemplateDraftState {
  title: string;
  description: string;
  type: string;
  priority: TemplatePriority;
  estimate: string;
  checklist: readonly string[];
  pendingChecklistItem: string;
}

export interface TemplateDraftController {
  values: TemplateDraftState;
  set: <K extends keyof TemplateDraftState>(field: K, value: TemplateDraftState[K]) => void;
  addChecklistItem: () => void;
  removeChecklistItem: (index: number) => void;

  isComplete: boolean;

  toDraft: (submitForApproval: boolean) => TemplateDraft;
}

export function useTemplateDraft(existing?: TaskTemplate): TemplateDraftController {
  const [values, setValues] = useState<TemplateDraftState>({
    title: existing?.title ?? '',
    description: existing?.description ?? '',
    type: existing?.type ?? '',
    priority: existing?.priority ?? 'NORMAL',
    estimate:
      existing?.estimatedHours === null || existing?.estimatedHours === undefined
        ? ''
        : `${existing.estimatedHours}`,
    checklist: existing?.checklist ?? [],
    pendingChecklistItem: '',
  });

  const set: TemplateDraftController['set'] = (field, value) =>
    setValues((current) => ({ ...current, [field]: value }));

  return {
    values,
    set,
    addChecklistItem: () =>
      setValues((current) =>
        current.pendingChecklistItem.trim() === ''
          ? current
          : {
              ...current,
              checklist: [...current.checklist, current.pendingChecklistItem.trim()],
              pendingChecklistItem: '',
            },
      ),
    removeChecklistItem: (index) =>
      setValues((current) => ({
        ...current,
        checklist: current.checklist.filter((_, at) => at !== index),
      })),
    isComplete: values.title.trim() !== '',
    toDraft: (submitForApproval) => ({
      title: values.title.trim(),
      description: values.description.trim() === '' ? undefined : values.description.trim(),
      type: values.type.trim() === '' ? undefined : values.type.trim(),
      priority: values.priority,
      estimatedHours: values.estimate.trim() === '' ? null : Number(values.estimate),
      checklist: [...values.checklist],
      submitForApproval,
    }),
  };
}
