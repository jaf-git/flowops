export type WorkspaceUse = 'WORK' | 'PERSONAL';

export interface SetupDraft {
  ownerName: string;
  workspaceName: string;
  use: WorkspaceUse;
  timezone: string;
}

export interface SetupStep {
  field: keyof SetupDraft;
  satisfied: boolean;

  answer: string;
}

export function stepsOf(draft: SetupDraft): SetupStep[] {
  return [
    {
      field: 'ownerName',
      satisfied: draft.ownerName.trim() !== '',
      answer: draft.ownerName.trim(),
    },
    {
      field: 'workspaceName',
      satisfied: draft.workspaceName.trim() !== '',
      answer: draft.workspaceName.trim(),
    },
    { field: 'use', satisfied: true, answer: draft.use },
    { field: 'timezone', satisfied: draft.timezone !== '', answer: draft.timezone },
  ];
}

export function satisfiedCount(draft: SetupDraft): number {
  return stepsOf(draft).filter((step) => step.satisfied).length;
}

export function isReady(draft: SetupDraft): boolean {
  return stepsOf(draft).every((step) => step.satisfied);
}
