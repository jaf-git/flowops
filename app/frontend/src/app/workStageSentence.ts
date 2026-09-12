import type { TFunction } from 'i18next';

import type { WorkStage } from '../shared/model/workPipeline';

export function stageSentence(stage: WorkStage, t: TFunction): string {
  switch (stage.kind) {
    case 'MESSAGE':
      return t('pipeline.said.body');
    case 'TASK':
      return t('pipeline.task.body', { title: String(stage.facts.title ?? '') });
    case 'TEMPLATE':
      return stage.facts.draft === 'DRAFT'
        ? t('pipeline.template.draft', { title: String(stage.facts.title ?? '') })
        : t('pipeline.template.approved', { title: String(stage.facts.title ?? '') });
    case 'ANALYSIS':
      return t('pipeline.analysis.body', { count: Number(stage.facts.stamped ?? 0) });
    case 'PLANNED_IN':
      return t('pipeline.plannedIn.body', {
        name: String(stage.facts.firstProcess ?? ''),
        count: Number(stage.facts.runs ?? 0),
      });
    case 'OBSERVATIONS':
      return t('pipeline.observations.body', { count: Number(stage.facts.count ?? 0) });
    default:
      return '';
  }
}
