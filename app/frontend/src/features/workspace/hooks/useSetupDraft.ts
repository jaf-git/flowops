import { useState } from 'react';

import type { SetupPrefill } from '../api/workspaceApi';
import { preselectedTimezone } from '../model/timezone';
import type { SetupDraft } from '../model/setupDraft';

export interface SetupDraftHandle {
  draft: SetupDraft;
  change: <K extends keyof SetupDraft>(field: K, value: SetupDraft[K]) => void;
}

export function useSetupDraft(prefill: SetupPrefill): SetupDraftHandle {
  const [draft, setDraft] = useState<SetupDraft>(() => ({
    ownerName: '',
    workspaceName: '',
    use: 'WORK',
    timezone: preselectedTimezone(prefill.availableTimezones, prefill.suggestedTimezone),
  }));

  return {
    draft,
    change: (field, value) => setDraft((current) => ({ ...current, [field]: value })),
  };
}
