import { useQuery } from '@tanstack/react-query';
import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { useWorkVocabulary } from '../../../shared/model/workVocabulary';

interface TemplateLineageProps {
  taskTemplateId: string;
  locale: string;

  onOpenTask?: (taskId: string) => void;
}

export function TemplateLineage({
  taskTemplateId,
  locale,
  onOpenTask,
}: TemplateLineageProps): JSX.Element | null {
  const { t } = useTranslation();

  const vocabulary = useWorkVocabulary();
  const uses = useQuery({
    queryKey: ['task-templates', taskTemplateId, 'uses'],
    queryFn: () => vocabulary.lineageOf(taskTemplateId),

    staleTime: 10_000,
    refetchOnWindowFocus: true,
  });

  if (uses.isPending || uses.isError || uses.data === undefined) {
    return null;
  }

  const { processTemplates, runs, runsTotal } = uses.data;
  if (processTemplates.length === 0 && runs.length === 0) {
    return (
      <section className="fo-lineage">
        <h2 className="fo-lineage-heading">{t('tasklib.lineage.heading')}</h2>
        <p className="fo-lineage-empty">{t('tasklib.lineage.nothingUsesThis')}</p>
      </section>
    );
  }

  return (
    <section className="fo-lineage">
      <h2 className="fo-lineage-heading">{t('tasklib.lineage.heading')}</h2>

      {processTemplates.length > 0 && (
        <>
          <h3 className="fo-lineage-sub">
            {t('tasklib.lineage.plannedIn', { count: processTemplates.length })}
          </h3>
          <ul className="fo-lineage-list">
            {processTemplates.map((planned) => (
              <li key={planned.templateId}>
                <span className="fo-lineage-name">{planned.name}</span>
                <span className="fo-lineage-meta">
                  {t('tasklib.lineage.atStep', { position: planned.position })}
                </span>
                {!planned.active && (
                  <span className="fo-lineage-meta">{t('tasklib.lineage.retired')}</span>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {runs.length > 0 && (
        <>
          <h3 className="fo-lineage-sub">{t('tasklib.lineage.cutIn', { count: runsTotal })}</h3>
          <ul className="fo-lineage-list">
            {runs.map((run) => (
              <li key={run.stepId}>
                <Link to={`/${locale}/canvas/process/${run.instanceId}`}>{run.instanceName}</Link>
                <span className="fo-lineage-meta">{t(`process.state.${run.instanceState}`)}</span>
                <span className="fo-lineage-meta">
                  {t(`process.condition.${run.stepCondition}`)}
                </span>

                {run.taskId === null ? (
                  <span className="fo-lineage-meta">{t('tasklib.lineage.noTaskYet')}</span>
                ) : onOpenTask === undefined ? null : (
                  <button
                    type="button"
                    className="fo-lineage-open"
                    onClick={() => {
                      onOpenTask(run.taskId as string);
                    }}
                  >
                    {t('tasklib.lineage.openTask')}
                  </button>
                )}
              </li>
            ))}
          </ul>
          {runsTotal > runs.length && (
            <p className="fo-lineage-meta">
              {t('tasklib.lineage.showingSome', { shown: runs.length, total: runsTotal })}
            </p>
          )}
        </>
      )}
    </section>
  );
}
