import {
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  addTaskToInstance,
  assignStep,
  skipStep,
  authorTemplate,
  drawDependency,
  drawInstanceDependency,
  editTemplate,
  eraseDependency,
  eraseInstanceDependency,
  fetchAssignablePeople,
  fetchAttachableTasks,
  fetchInstance,
  fetchInstances,
  fetchTasksForANewProcess,
  fetchTemplate,
  fetchTemplates,
  removeTaskFromInstance,
  reorderInstanceTasks,
  startInstance,
  startInstanceFromTasks,
  type AttachableTask,
  type Candidate,
  type Dependency,
  type Instance,
  abandonInstance,
  retireTemplate,
  type InstancePopulation,
  type InstanceSummary,
  type NewProcessTask,
  type NewTemplateStep,
  type Template,
  type TemplateSummary,
} from '../api/processApi';

const TEMPLATES = ['process-templates'] as const;
const INSTANCES = ['process-instances'] as const;

const POPULATIONS = ['ON_THE_BOARD', 'EVERY_RUN'] as const satisfies readonly InstancePopulation[];

function listKey(population: InstancePopulation): readonly unknown[] {
  return [...INSTANCES, 'list', population];
}

function useTemplateChange<TInput>(
  change: (input: TInput) => Promise<Template>,
): UseMutationResult<Template, Error, TInput> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: change,
    onSuccess: (template) => {
      cache.setQueryData([...TEMPLATES, template.id], template);
      void cache.invalidateQueries({ queryKey: TEMPLATES });
    },
  });
}

function useInstanceChange<TInput>(
  change: (input: TInput) => Promise<Instance>,
): UseMutationResult<Instance, Error, TInput> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: change,
    onSuccess: (instance) => {
      cache.setQueryData([...INSTANCES, instance.id], instance);
      void cache.invalidateQueries({ queryKey: INSTANCES });
    },
  });
}

export function useTemplates(): UseQueryResult<{ templates: TemplateSummary[] }, Error> {
  return useQuery({ queryKey: TEMPLATES, queryFn: fetchTemplates, retry: false });
}

export function useTemplate(id: string | null): UseQueryResult<Template, Error> {
  return useQuery({
    queryKey: [...TEMPLATES, id],
    queryFn: () => fetchTemplate(id as string),
    enabled: id !== null,
    retry: false,
  });
}

export function useAuthorTemplate(): UseMutationResult<
  Template,
  Error,
  { name: string; overview: string | null; steps: NewTemplateStep[] }
> {
  return useTemplateChange(authorTemplate);
}

export function useEditTemplate(
  id: string,
): UseMutationResult<Template, Error, { overview: string | null; steps: NewTemplateStep[] }> {
  return useTemplateChange((input) => editTemplate(id, input));
}

export function useDrawDependency(id: string): UseMutationResult<Template, Error, Dependency> {
  return useTemplateChange((edge) => drawDependency(id, edge));
}

export function useEraseDependency(id: string): UseMutationResult<Template, Error, Dependency> {
  return useTemplateChange((edge) => eraseDependency(id, edge));
}

export function useInstances(
  population: InstancePopulation = 'ON_THE_BOARD',
): UseQueryResult<{ instances: InstanceSummary[] }, Error> {
  return useQuery({
    queryKey: listKey(population),
    queryFn: () => fetchInstances(population),
    retry: false,
  });
}

export function useInstancesInFull(population: InstancePopulation = 'ON_THE_BOARD'): {
  instances: Instance[];
  isPending: boolean;
  incomplete: boolean;
} {
  const summaries = useInstances(population);
  const ids = (summaries.data?.instances ?? []).map((instance) => instance.id);

  const details = useQueries({
    queries: ids.map((id) => ({
      queryKey: [...INSTANCES, id],
      queryFn: () => fetchInstance(id),
      retry: false,
    })),
  });

  return {
    instances: details
      .map((result) => result.data)
      .filter((instance): instance is Instance => instance !== undefined),
    isPending: summaries.isPending || details.some((result) => result.isPending),
    incomplete: summaries.isError || details.some((result) => result.isError),
  };
}

export function useInstance(id: string | null): UseQueryResult<Instance, Error> {
  return useQuery({
    queryKey: [...INSTANCES, id],
    queryFn: () => fetchInstance(id as string),
    enabled: id !== null,
    retry: false,
  });
}

export function useAssignablePeople(
  instanceId: string | null,
): UseQueryResult<{ people: Candidate[] }, Error> {
  return useQuery({
    queryKey: [...INSTANCES, instanceId, 'assignable-people'],
    queryFn: () => fetchAssignablePeople(instanceId as string),
    enabled: instanceId !== null,
    retry: false,
  });
}

export function useStartInstance(): UseMutationResult<
  Instance,
  Error,
  { templateId: string; name: string; processOwnerId: string }
> {
  return useInstanceChange(startInstance);
}

export function useSkipStep(
  instanceId: string,
): UseMutationResult<Instance, Error, { stepId: string }> {
  return useInstanceAndTaskChange((input: { stepId: string }) =>
    skipStep(instanceId, input.stepId),
  );
}

export function useAssignStep(
  instanceId: string,
): UseMutationResult<
  Instance,
  Error,
  { stepId: string; assigneeId: string; deadline: string | null }
> {
  return useInstanceAndTaskChange(
    (input: { stepId: string; assigneeId: string; deadline: string | null }) => {
      const { stepId, ...rest } = input;
      return assignStep(instanceId, stepId, rest);
    },
  );
}

function useInstanceAndTaskChange<TInput>(
  change: (input: TInput) => Promise<Instance>,
): UseMutationResult<Instance, Error, TInput> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: change,
    onSuccess: (instance) => {
      cache.setQueryData([...INSTANCES, instance.id], instance);
      void cache.invalidateQueries({ queryKey: INSTANCES });
      void cache.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
}

export function useAttachableTasks(
  instanceId: string | null,
): UseQueryResult<{ tasks: AttachableTask[] }, Error> {
  return useQuery({
    queryKey: [...INSTANCES, instanceId, 'attachable-tasks'],
    queryFn: () => fetchAttachableTasks(instanceId as string),
    enabled: instanceId !== null,
    retry: false,
  });
}

export function useTasksForANewProcess(
  wanted: boolean,
): UseQueryResult<{ tasks: AttachableTask[] }, Error> {
  return useQuery({
    queryKey: [...INSTANCES, 'attachable-tasks', 'for-a-new-process'],
    queryFn: fetchTasksForANewProcess,
    enabled: wanted,
    retry: false,
  });
}

export function useStartInstanceFromTasks(): UseMutationResult<
  Instance,
  Error,
  { name: string; processOwnerId: string; taskIds: string[] }
> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: startInstanceFromTasks,
    onSuccess: (instance) => {
      cache.setQueryData([...INSTANCES, instance.id], instance);
      for (const population of POPULATIONS) {
        cache.setQueryData(
          listKey(population),
          (current: { instances: InstanceSummary[] } | undefined) => ({
            instances: [summarise(instance), ...(current?.instances ?? [])],
          }),
        );
      }
      void cache.invalidateQueries({ queryKey: INSTANCES });
      void cache.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
}

function summarise(instance: Instance): InstanceSummary {
  return {
    id: instance.id,
    name: instance.name,
    templateName: instance.templateName,
    state: instance.state,
    progress: instance.progress,
    awaitingAssignmentCount: instance.awaitingAssignment.length,
  };
}

export function useAddTaskToInstance(
  instanceId: string,
): UseMutationResult<
  Instance,
  Error,
  { taskId?: string; newTask?: NewProcessTask; dependsOnStepIds: string[] }
> {
  return useInstanceAndTaskChange((input) => addTaskToInstance(instanceId, input));
}

export function useAddTaskToChosenInstance(): UseMutationResult<
  Instance,
  Error,
  { instanceId: string; taskId?: string; newTask?: NewProcessTask; dependsOnStepIds: string[] }
> {
  return useInstanceAndTaskChange(({ instanceId, ...input }) =>
    addTaskToInstance(instanceId, input),
  );
}

export function useRemoveTaskFromInstance(
  instanceId: string,
): UseMutationResult<Instance, Error, string> {
  return useInstanceAndTaskChange((stepId: string) => removeTaskFromInstance(instanceId, stepId));
}

export function useReorderInstanceTasks(
  instanceId: string,
): UseMutationResult<Instance, Error, string[]> {
  return useInstanceChange((stepIds: string[]) => reorderInstanceTasks(instanceId, stepIds));
}

export function useDrawInstanceDependency(
  instanceId: string,
): UseMutationResult<Instance, Error, Dependency> {
  return useInstanceChange((edge: Dependency) => drawInstanceDependency(instanceId, edge));
}

export function useEraseInstanceDependency(
  instanceId: string,
): UseMutationResult<Instance, Error, Dependency> {
  return useInstanceChange((edge: Dependency) => eraseInstanceDependency(instanceId, edge));
}

export function useRetireTemplate(id: string): UseMutationResult<Template, Error, void> {
  return useTemplateChange(() => retireTemplate(id));
}

export function useAbandonInstance(id: string): UseMutationResult<Instance, Error, string> {
  return useInstanceAndTaskChange((reason: string) => abandonInstance(id, reason));
}
