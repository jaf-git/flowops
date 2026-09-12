import type { JSX, ReactNode } from 'react';
import type { AssignablePerson } from '../../../shared/model/people';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { Chip } from '../../../shared/ui/Chip';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { Spinner } from '../../../shared/ui/Spinner';
import { SchedulePanel } from '../components/SchedulePanel';
import { TemplateLineage } from '../components/TemplateLineage';
import { UsageBands, type UsageBandsProps } from '../components/UsageBands';
import { useTemplate, useTemplateUsage } from '../hooks/useTemplateUsage';

interface TemplateUsageScreenProps {
  templateId: string;

  shapeSuggestion?: (templateId: string, approved: boolean) => ReactNode;

  insightsFor?: (templateId: string) => ReactNode;

  assignablePeople: readonly AssignablePerson[];

  locale: string;

  renderTasks: UsageBandsProps['renderTasks'];

  onOpenTask?: (taskId: string) => void;
}

export function TemplateUsageScreen({
  templateId,
  insightsFor,
  assignablePeople,
  locale,
  renderTasks,
  shapeSuggestion,
  onOpenTask,
}: TemplateUsageScreenProps): JSX.Element {
  const { t } = useTranslation();
  const template = useTemplate(templateId);
  const usage = useTemplateUsage(templateId);

  const back = (
    <Link className="fo-usage-back" to={`/${locale}`}>
      {t('tasklib.usage.back')}
    </Link>
  );

  if (template.isPending || usage.isPending) {
    return (
      <div className="fo-usage">
        {back}
        <Spinner label={t('tasklib.usage.loading')} />
      </div>
    );
  }

  if (
    template.isError ||
    usage.isError ||
    template.data === undefined ||
    usage.data === undefined
  ) {
    return (
      <div className="fo-usage">
        {back}
        <EmptyState heading={t('tasklib.usage.notFound')} body={t('tasklib.usage.notFoundBody')} />
      </div>
    );
  }

  return (
    <div className="fo-usage">
      {back}

      <header className="fo-usage-head">
        <h1 className="fo-usage-title">{template.data.title}</h1>

        {template.data.status !== 'APPROVED' && (
          <Chip tone={template.data.status === 'RETIRED' ? 'neutral' : 'waiting'}>
            {t(`tasklib.status.${template.data.status}`)}
          </Chip>
        )}
        <span className="fo-usage-stamped">
          {t('tasklib.card.used', { count: usage.data.stamped })}
        </span>
      </header>

      {template.data.description !== null && template.data.description !== '' && (
        <p className="fo-usage-desc">{template.data.description}</p>
      )}

      <UsageBands usage={usage.data} renderTasks={renderTasks} />

      <TemplateLineage taskTemplateId={templateId} locale={locale} onOpenTask={onOpenTask} />

      {insightsFor?.(templateId)}

      {shapeSuggestion?.(templateId, template.data.status === 'APPROVED')}

      <SchedulePanel
        assignablePeople={assignablePeople}
        templateId={templateId}
        usable={template.data.status === 'APPROVED'}
        locale={locale}
      />
    </div>
  );
}
