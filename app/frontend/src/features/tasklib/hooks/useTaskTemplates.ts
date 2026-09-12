import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  approveTemplate,
  approveTemplates,
  copyTemplate,
  createTemplate,
  editTemplate,
  fetchApprovalQueue,
  fetchDraftCandidates,
  fetchTemplateLibrary,
  fetchTemplateSuggestions,
  checkTemplateShape,
  recordTemplateMetadata,
  sendTemplateBack,
  retireTemplate,
  stampTaskFromTemplate,
  type MetadataField,
  type StampTaskInput,
  type StampedTask,
  type DraftCandidate,
  type TaskTemplate,
  type TemplateDraft,
  type TemplateLibrary,
  type ProcessShapeHint,
  type TemplateQuery,
  type TemplateSuggestion,
} from '../api/taskTemplateApi';

const TEMPLATES_KEY = ['task-templates'] as const;

export function useTemplateLibrary(query: TemplateQuery): UseQueryResult<TemplateLibrary, Error> {
  return useQuery({
    queryKey: [...TEMPLATES_KEY, 'library', query],
    queryFn: () => fetchTemplateLibrary(query),
    retry: false,
    placeholderData: (previous) => previous,
  });
}

export function useApprovalQueue(mayApprove: boolean): UseQueryResult<TaskTemplate[], Error> {
  return useQuery({
    queryKey: [...TEMPLATES_KEY, 'approval-queue'],
    queryFn: fetchApprovalQueue,
    retry: false,
    enabled: mayApprove,
  });
}

export function useDraftCandidates(mayApprove: boolean): UseQueryResult<DraftCandidate[], Error> {
  return useQuery({
    queryKey: [...TEMPLATES_KEY, 'draft-candidates'],
    queryFn: fetchDraftCandidates,
    retry: false,
    enabled: mayApprove,
  });
}

function useTemplateMutation<TInput, TResult>(
  run: (input: TInput) => Promise<TResult>,
): UseMutationResult<TResult, Error, TInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: run,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TEMPLATES_KEY }),
  });
}

export function useCreateTemplate(): UseMutationResult<TaskTemplate, Error, TemplateDraft> {
  return useTemplateMutation(createTemplate);
}

export function useEditTemplate(): UseMutationResult<
  TaskTemplate,
  Error,
  { id: string; draft: TemplateDraft }
> {
  return useTemplateMutation(editTemplate);
}

export function useApproveTemplate(): UseMutationResult<
  TaskTemplate,
  Error,
  { id: string; edited?: TemplateDraft }
> {
  return useTemplateMutation(approveTemplate);
}

export function useApproveTemplates(): UseMutationResult<TaskTemplate[], Error, string[]> {
  return useTemplateMutation(approveTemplates);
}

export function useSendTemplateBack(): UseMutationResult<
  TaskTemplate,
  Error,
  { id: string; reason: string }
> {
  return useTemplateMutation(sendTemplateBack);
}

export function useShapeCheck(draft: {
  title: string;
  checklist: readonly string[];
  enabled: boolean;
}): UseQueryResult<ProcessShapeHint[], Error> {
  return useQuery({
    queryKey: [...TEMPLATES_KEY, 'shape', draft.title, draft.checklist],
    queryFn: () => checkTemplateShape({ title: draft.title, checklist: [...draft.checklist] }),
    enabled: draft.enabled && draft.checklist.length > 0,
    retry: false,
    placeholderData: (previous) => previous,
  });
}

export function useTemplateSuggestions(
  title: string,
  enabled: boolean,
): UseQueryResult<TemplateSuggestion[], Error> {
  const trimmed = title.trim();
  return useQuery({
    queryKey: [...TEMPLATES_KEY, 'suggestions', trimmed],
    queryFn: () => fetchTemplateSuggestions(trimmed),
    enabled: enabled && trimmed.length >= 3,
    retry: false,
    placeholderData: (previous) => previous,
  });
}

export function useRetireTemplate(): UseMutationResult<TaskTemplate, Error, string> {
  return useTemplateMutation(retireTemplate);
}

export function useCopyTemplate(): UseMutationResult<TaskTemplate, Error, string> {
  return useTemplateMutation(copyTemplate);
}

export function useStampTask(): UseMutationResult<
  StampedTask,
  Error,
  { id: string; task: StampTaskInput }
> {
  return useTemplateMutation(stampTaskFromTemplate);
}

export function useRecordTemplateMetadata(): UseMutationResult<
  TaskTemplate,
  Error,
  { id: string; field: MetadataField; answer: string }
> {
  return useTemplateMutation(recordTemplateMetadata);
}
