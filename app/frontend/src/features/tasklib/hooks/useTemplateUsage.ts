import { useQuery, type UseQueryResult } from '@tanstack/react-query';

import {
  fetchTemplate,
  fetchTemplateUsage,
  type TaskTemplate,
  type TemplateUsage,
} from '../api/taskTemplateApi';

const TEMPLATES_KEY = ['task-templates'] as const;

export function useTemplate(id: string): UseQueryResult<TaskTemplate, Error> {
  return useQuery({
    queryKey: [...TEMPLATES_KEY, 'one', id],
    queryFn: () => fetchTemplate(id),
    retry: false,
  });
}

export function useTemplateUsage(id: string): UseQueryResult<TemplateUsage, Error> {
  return useQuery({
    queryKey: [...TEMPLATES_KEY, 'usage', id],
    queryFn: () => fetchTemplateUsage(id),
    retry: false,
  });
}
