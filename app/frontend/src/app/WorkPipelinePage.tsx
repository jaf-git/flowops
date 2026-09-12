import type { JSX } from 'react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import type { SupportedLocale } from '../i18n';
import type { WorkStage } from '../shared/model/workPipeline';
import { EmptyState } from '../shared/ui/EmptyState';
import { FilterBar } from '../shared/ui/FilterBar';
import { PageHeader } from '../shared/ui/PageHeader';
import { useTaskDetail, useTasks } from '../features/task';
import { useWorkPipeline } from './useWorkPipeline';
import { stageSentence } from './workStageSentence';

interface WorkPipelinePageProps {
  locale: SupportedLocale;

  onOpenTask: (taskId: string) => void;
}

export function WorkPipelinePage({ locale, onOpenTask }: WorkPipelinePageProps): JSX.Element {
  const { t } = useTranslation();
  const tasks = useTasks();
  const [chosen, setChosen] = useState<string | undefined>(undefined);
  const [query, setQuery] = useState('');

  const all = tasks.data?.tasks ?? [];

  const needle = query.trim().toLowerCase();
  const shown =
    needle === '' ? all : all.filter((task) => task.title.toLowerCase().includes(needle));

  return (
    <div className="fo-page">
      <PageHeader title={t('pipeline.page.heading')} subtitle={t('pipeline.page.lead')} />

      <div className="fo-page-record">
        <section className="ui-card fo-panel" aria-label={t('pipeline.page.pick')}>
          <FilterBar
            query={query}
            onQuery={setQuery}
            labels={{
              search: t('pipeline.page.search'),
              searchPlaceholder: t('pipeline.page.searchPlaceholder'),
              presets: t('pipeline.page.search'),
              clear: t('pipeline.page.clear'),
            }}
          />

          {tasks.isPending ? (
            <p className="fo-chain-pick-state">{t('pipeline.page.loading')}</p>
          ) : tasks.isError ? (
            <EmptyState
              heading={t('pipeline.page.cannotList')}
              body={t('pipeline.page.cannotListBody')}
            />
          ) : shown.length === 0 ? (
            <EmptyState heading={t('pipeline.page.noWork')} body={t('pipeline.page.noWorkBody')} />
          ) : (
            <ul className="fo-chain-picker">
              {shown.map((task) => (
                <li key={task.id}>
                  <button
                    type="button"
                    className="fo-chain-pick"
                    aria-current={task.id === chosen ? 'true' : undefined}
                    onClick={() => setChosen(task.id)}
                  >
                    <span className="fo-chain-pick-title">{task.title}</span>
                    <span className="fo-chain-pick-state">{t(`task.state.${task.state}`)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {chosen === undefined ? (
          <section
            className="ui-card fo-panel fo-chain-detail"
            aria-label={t('pipeline.page.heading')}
          >
            <EmptyState
              heading={t('pipeline.page.nothingChosen')}
              body={t('pipeline.page.nothingChosenBody')}
            />
          </section>
        ) : (
          <WorkChain key={chosen} taskId={chosen} locale={locale} onOpenTask={onOpenTask} />
        )}
      </div>
    </div>
  );
}

function WorkChain({
  taskId,
  locale,
  onOpenTask,
}: {
  taskId: string;
  locale: SupportedLocale;
  onOpenTask: (taskId: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  const detail = useTaskDetail(taskId);

  const { stages } = useWorkPipeline({
    taskId,
    taskTitle: detail.data?.title ?? '',
    taskState: detail.data?.state ?? '',
    templateId: detail.data?.templateId ?? null,
  });

  if (detail.isError) {
    return (
      <section className="ui-card fo-panel fo-chain-detail" aria-label={t('pipeline.page.heading')}>
        <EmptyState
          heading={t('pipeline.page.cannotRead')}
          body={t('pipeline.page.cannotReadBody')}
        />
      </section>
    );
  }

  return (
    <section
      className="ui-card fo-panel fo-chain-page fo-chain-detail"
      aria-label={t('pipeline.page.heading')}
    >
      <header className="fo-chain-page-head">
        <div>
          <h3 className="fo-panel-title">{detail.data?.title ?? ''}</h3>
          <p className="fo-chain-page-birth">
            {t(`pipeline.birth.${String(stages[0]?.facts.birth ?? 'TYPED')}`, {
              defaultValue: '',
            })}
          </p>
        </div>

        <button
          type="button"
          className="ui-button ui-button-quiet"
          onClick={() => onOpenTask(taskId)}
        >
          {t('pipeline.page.openTask')}
        </button>
      </header>

      <ol className="fo-chain-track">
        {stages.map((stage, index) => (
          <li key={`${stage.kind}-${stage.subjectId}`} className="fo-chain-card">
            <span className="fo-eyebrow">{t(`pipeline.stage.${stage.kind}`)}</span>
            <span className="fo-chain-card-body">{stageSentence(stage, t)}</span>
            <StageLink stage={stage} locale={locale} label={t('pipeline.page.evidence')} />

            {index < stages.length - 1 ? (
              <span className="fo-chain-arrow" aria-hidden="true" />
            ) : null}
          </li>
        ))}
      </ol>
    </section>
  );
}

function StageLink({
  stage,
  locale,
  label,
}: {
  stage: WorkStage;
  locale: SupportedLocale;
  label: string;
}): JSX.Element | null {
  if (stage.kind === 'TASK' || stage.kind === 'MESSAGE') {
    return null;
  }
  return (
    <Link className="fo-chain-card-link" to={`/${locale}/templates/${stage.subjectId}`}>
      {label}
    </Link>
  );
}
