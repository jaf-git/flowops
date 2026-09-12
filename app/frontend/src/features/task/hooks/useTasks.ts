import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  acceptTask,
  approveTask,
  commentOnTask,
  fetchTaskActivity,
  overrideTask,
  reassignTask,
  decideDeadline,
  acknowledgeDeadlineNotice,
  addChecklistItem,
  attachLink,
  detachLink,
  fetchTaskMaterial,
  removeChecklistItem,
  tickChecklistItem,
  editTask,
  fetchDeadlineNotices,
  setDeadline,
  proposeDeadline,
  rejectTask,
  blockTask,
  closeTask,
  completeTask,
  createTask,
  fetchAssignablePeople,
  fetchReviewQueue,
  fetchTaskDetail,
  fetchTaskSection,
  fetchTaskSections,
  fetchTasks,
  returnTaskForRework,
  startTask,
  unblockTask,
  type ActivityEntry,
  type PagedTasks,
  type TaskQueryFilter,
  type TaskSectionKey,
  type TaskSections,
  type TaskSortKey,
  type ApproveTaskInput,
  type AssignablePerson,
  type CommentOnTaskInput,
  type OverrideTaskInput,
  type ReassignTaskInput,
  type BlockTaskInput,
  type CompleteTaskInput,
  type DecideDeadlineInput,
  type DeadlineNotice,
  type ChecklistItem,
  type LinkRole,
  type TaskLink,
  type TaskMaterial,
  type EditTaskInput,
  type SetDeadlineInput,
  type ProposeDeadlineInput,
  type RejectTaskInput,
  type CreateTaskInput,
  type ReturnTaskInput,
  type Task,
  type TaskDetail,
  type TaskSummary,
  type UnblockTaskInput,
} from '../api/taskApi';

const TASKS_QUERY_KEY = ['tasks'] as const;
const REVIEW_QUEUE_QUERY_KEY = ['tasks', 'review-queue'] as const;
const DEADLINE_NOTICES_QUERY_KEY = ['tasks', 'deadline-notices'] as const;

export function useTasks(): UseQueryResult<{ tasks: TaskSummary[] }, Error> {
  return useQuery({ queryKey: TASKS_QUERY_KEY, queryFn: fetchTasks, retry: false });
}

export function useTaskSections(filter: TaskQueryFilter): UseQueryResult<TaskSections, Error> {
  return useQuery({
    queryKey: [...TASKS_QUERY_KEY, 'sections', filter],
    queryFn: () => fetchTaskSections(filter),
    retry: false,

    placeholderData: (previous) => previous,
  });
}

export function useTaskSection(input: {
  section: TaskSectionKey;
  page: number;
  size: number;
  sort: TaskSortKey;
  filter: TaskQueryFilter;
  enabled: boolean;
}): UseQueryResult<PagedTasks, Error> {
  return useQuery({
    queryKey: [
      ...TASKS_QUERY_KEY,
      'section',
      input.section,
      input.page,
      input.size,
      input.sort,
      input.filter,
    ],
    queryFn: () =>
      fetchTaskSection({
        section: input.section,
        page: input.page,
        size: input.size,
        sort: input.sort,
        filter: input.filter,
      }),
    enabled: input.enabled,
    retry: false,
    placeholderData: (previous) => previous,
  });
}

export function useCreateTask(): UseMutationResult<Task, Error, CreateTaskInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createTask,
    retry: false,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TASKS_QUERY_KEY }),
  });
}

export function useAcceptTask(): UseMutationResult<Task, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: acceptTask,
    retry: false,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TASKS_QUERY_KEY }),
  });
}

export function useStartTask(): UseMutationResult<Task, Error, string> {
  return useTaskTransition(startTask);
}

export function useBlockTask(): UseMutationResult<Task, Error, BlockTaskInput> {
  return useTaskTransition(blockTask);
}

export function useUnblockTask(): UseMutationResult<Task, Error, UnblockTaskInput> {
  return useTaskTransition(unblockTask);
}

export function useCompleteTask(): UseMutationResult<Task, Error, CompleteTaskInput> {
  return useTaskTransition(completeTask);
}

export function useRejectTask(): UseMutationResult<Task, Error, RejectTaskInput> {
  return useTaskTransition(rejectTask);
}

export function useProposeDeadline(): UseMutationResult<Task, Error, ProposeDeadlineInput> {
  return useTaskTransition(proposeDeadline);
}

export function useDecideDeadline(): UseMutationResult<Task, Error, DecideDeadlineInput> {
  return useTaskTransition(decideDeadline);
}

const MATERIAL_QUERY_KEY = (id: string) => ['tasks', 'material', id] as const;

export function useTaskMaterial(id: string | undefined): UseQueryResult<TaskMaterial, Error> {
  return useQuery({
    queryKey: MATERIAL_QUERY_KEY(id ?? 'none'),
    queryFn: () => fetchTaskMaterial(id as string),
    enabled: id !== undefined,
    retry: false,
  });
}

function useMaterialMutation<TInput extends { id: string }, TResult>(
  mutationFn: (input: TInput) => Promise<TResult>,
): UseMutationResult<TResult, Error, TInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (_result, input) =>
      queryClient.invalidateQueries({ queryKey: MATERIAL_QUERY_KEY(input.id) }),
  });
}

export function useAttachLink(): UseMutationResult<
  TaskLink,
  Error,
  { id: string; url: string; label: string; role: LinkRole }
> {
  return useMaterialMutation(attachLink);
}

export function useDetachLink(): UseMutationResult<void, Error, { id: string; linkId: string }> {
  return useMaterialMutation(detachLink);
}

export function useAddChecklistItem(): UseMutationResult<
  ChecklistItem,
  Error,
  { id: string; text: string }
> {
  return useMaterialMutation(addChecklistItem);
}

export function useTickChecklistItem(): UseMutationResult<
  ChecklistItem,
  Error,
  { id: string; itemId: string; done: boolean }
> {
  return useMaterialMutation(tickChecklistItem);
}

export function useRemoveChecklistItem(): UseMutationResult<
  void,
  Error,
  { id: string; itemId: string }
> {
  return useMaterialMutation(removeChecklistItem);
}

export function useEditTask(): UseMutationResult<Task, Error, EditTaskInput> {
  return useTaskTransition(editTask);
}

export function useSetDeadline(): UseMutationResult<Task, Error, SetDeadlineInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: setDeadline,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: TASKS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: DEADLINE_NOTICES_QUERY_KEY });
    },
  });
}

export function useDeadlineNotices(): UseQueryResult<{ notices: DeadlineNotice[] }, Error> {
  return useQuery({
    queryKey: DEADLINE_NOTICES_QUERY_KEY,
    queryFn: fetchDeadlineNotices,
    retry: false,
  });
}

export function useAcknowledgeDeadlineNotice(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: acknowledgeDeadlineNotice,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DEADLINE_NOTICES_QUERY_KEY }),
  });
}

function useTaskTransition<TInput>(
  mutationFn: (input: TInput) => Promise<Task>,
): UseMutationResult<Task, Error, TInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    retry: false,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TASKS_QUERY_KEY }),
  });
}

export function useAssignablePeople(): UseQueryResult<{ people: AssignablePerson[] }, Error> {
  return useQuery({
    queryKey: ['tasks', 'assignable-people'],
    queryFn: fetchAssignablePeople,
    retry: false,
  });
}

export function useReviewQueue(enabled: boolean): UseQueryResult<{ tasks: TaskSummary[] }, Error> {
  return useQuery({
    queryKey: REVIEW_QUEUE_QUERY_KEY,
    queryFn: fetchReviewQueue,
    retry: false,
    enabled,
  });
}

export function useTaskDetail(id: string | undefined): UseQueryResult<TaskDetail, Error> {
  return useQuery({
    queryKey: ['tasks', 'detail', id],
    queryFn: () => fetchTaskDetail(id as string),
    retry: false,
    enabled: id !== undefined,
  });
}

export function useApproveTask(): UseMutationResult<Task, Error, ApproveTaskInput> {
  return useReviewDecision(approveTask);
}

export function useReturnTaskForRework(): UseMutationResult<Task, Error, ReturnTaskInput> {
  return useReviewDecision(returnTaskForRework);
}

export function useCloseTask(): UseMutationResult<Task, Error, string> {
  return useReviewDecision(closeTask);
}

function useReviewDecision<TInput>(
  mutationFn: (input: TInput) => Promise<Task>,
): UseMutationResult<Task, Error, TInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    retry: false,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: TASKS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: REVIEW_QUEUE_QUERY_KEY });
    },
  });
}

const ACTIVITY_QUERY_KEY = (id: string) => ['tasks', 'activity', id] as const;

export function useTaskActivity(
  id: string | undefined,
): UseQueryResult<{ entries: ActivityEntry[] }, Error> {
  return useQuery({
    queryKey: ACTIVITY_QUERY_KEY(id ?? ''),
    queryFn: () => fetchTaskActivity(id as string),
    enabled: id !== undefined,
    retry: false,
  });
}

export function useCommentOnTask(): UseMutationResult<unknown, Error, CommentOnTaskInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: commentOnTask,
    retry: false,
    onSuccess: (_result, input) =>
      queryClient.invalidateQueries({ queryKey: ACTIVITY_QUERY_KEY(input.id) }),
  });
}

export function useReassignTask(): UseMutationResult<Task, Error, ReassignTaskInput> {
  return useMoveWithHistory(reassignTask);
}

export function useOverrideTask(): UseMutationResult<Task, Error, OverrideTaskInput> {
  return useMoveWithHistory(overrideTask);
}

function useMoveWithHistory<TInput extends { id: string }>(
  mutationFn: (input: TInput) => Promise<Task>,
): UseMutationResult<Task, Error, TInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    retry: false,
    onSuccess: (_result, input) => {
      void queryClient.invalidateQueries({ queryKey: TASKS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ACTIVITY_QUERY_KEY(input.id) });
      void queryClient.invalidateQueries({ queryKey: ['tasks', 'detail', input.id] });
    },
  });
}
