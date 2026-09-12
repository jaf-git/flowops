import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { JSX, ReactNode } from 'react';
import { BrowserRouter } from 'react-router-dom';

import { fetchTaskOrigin } from '../features/chat';
import { insightsFor } from '../features/insight';
import { fetchTemplateUses } from '../features/process';
import { fetchTemplate, fetchTemplateLibrary, fetchTemplateUsage } from '../features/tasklib';
import { MotionProvider } from '../shared/motion/MotionProvider';
import { WorkVocabularyProvider } from '../shared/model/WorkVocabularyProvider';
import type { WorkVocabulary } from '../shared/model/workVocabulary';

import '../i18n';

const queryClient = new QueryClient();

const workVocabulary: WorkVocabulary = {
  listApproved: () =>
    fetchTemplateLibrary({ status: 'APPROVED', sort: 'MOST_USED', size: 100 }).then((page) =>
      page.templates.map((template) => ({
        id: template.id,
        title: template.title,
        timesUsed: template.timesUsed,
      })),
    ),
  lineageOf: (taskTemplateId) => fetchTemplateUses(taskTemplateId),

  originOf: (taskId) =>
    fetchTaskOrigin(taskId)
      .then((origin) => ({ conversationId: origin.conversationId, messageId: origin.messageId }))
      .catch(() => null),

  analysisOf: (taskTemplateId) =>
    fetchTemplateUsage(taskTemplateId).then((usage) => ({
      stamped: usage.stamped,
      medianActiveSeconds: usage.medianActiveSeconds,
      passedFirstTime: usage.passedFirstTime,
      reviewed: usage.reviewed,
    })),

  entryOf: (taskTemplateId) =>
    fetchTemplate(taskTemplateId).then((template) => ({
      title: template.title,
      draft: template.status === 'DRAFT',
    })),

  observationsOn: (subject, id) =>
    insightsFor(subject === 'TASK_TEMPLATE' ? 'task_template' : 'process_template', id).then(
      (found) => found.map((insight) => ({ kind: insight.kind, findingKey: insight.findingKey })),
    ),
};

interface ProvidersProps {
  children: ReactNode;
}

export function Providers({ children }: ProvidersProps): JSX.Element {
  return (
    <QueryClientProvider client={queryClient}>
      <WorkVocabularyProvider vocabulary={workVocabulary}>
        <MotionProvider>
          <BrowserRouter>{children}</BrowserRouter>
        </MotionProvider>
      </WorkVocabularyProvider>
    </QueryClientProvider>
  );
}
