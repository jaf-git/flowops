import { useId, type CSSProperties, type JSX } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { Chip } from '../../../shared/ui/Chip';
import { IconButton } from '../../../shared/ui/IconButton';
import { staggerFor } from '../../../shared/motion/tokens';
import type { TaskTemplate } from '../api/taskTemplateApi';

function enteringAt(along: number): CSSProperties {
  return { animationDelay: `${String(Math.round(staggerFor(along) * 1000))}ms` };
}

interface TemplateCardProps {
  template: TaskTemplate;

  along: number;

  busiest: number;
  mayRetire: boolean;
  onUse: () => void;
  onCopy: () => void;
  onRetire: () => void;
  onEdit: () => void;

  busy: boolean;
}

export function TemplateCard({
  template,
  along,
  busiest,
  mayRetire,
  onUse,
  onCopy,
  onRetire,
  onEdit,
  busy,
}: TemplateCardProps): JSX.Element {
  const { t, i18n } = useTranslation();
  const share = busiest === 0 ? 0 : Math.round((template.timesUsed / busiest) * 100);
  const usable = template.status === 'APPROVED';
  const refusalId = useId();

  return (
    <article className="fo-template-card" style={enteringAt(along)} data-status={template.status}>
      <header className="fo-template-head">
        <h3 className="fo-template-title">{template.title}</h3>

        {template.status !== 'APPROVED' && (
          <Chip tone={template.status === 'RETIRED' ? 'neutral' : 'waiting'}>
            {t(`tasklib.status.${template.status}`)}
          </Chip>
        )}

        {template.discoveredByPipeline && (
          <Chip tone="neutral">{t('tasklib.card.fromThePipeline')}</Chip>
        )}
      </header>

      <div className="fo-template-meta">
        {template.type !== null && template.type !== '' && (
          <span className="fo-template-type">{template.type}</span>
        )}
        <span className="fo-template-priority" data-priority={template.priority}>
          {t(`task.priority.${template.priority}`)}
        </span>
        {template.estimatedHours !== null && (
          <span className="fo-template-estimate">
            {t('tasklib.card.estimate', { hours: template.estimatedHours })}
          </span>
        )}
      </div>

      <p className="fo-template-desc">{template.description ?? ''}</p>

      <Link
        className="fo-template-usage"
        to={`/${i18n.language}/templates/${template.id}`}
        aria-label={t('tasklib.card.openUsage', { title: template.title })}
      >
        <span className="fo-template-used">
          {t('tasklib.card.used', { count: template.timesUsed })}
        </span>
        <span
          className="fo-template-bar"
          role="img"
          aria-label={t('tasklib.card.usedRelative', { percent: share })}
        >
          <span className="fo-template-bar-fill" style={{ width: `${share}%` }} />
        </span>
      </Link>

      {template.checklist.length > 0 && (
        <p className="fo-template-checklist">
          {t('tasklib.card.checklist', { count: template.checklist.length })}
        </p>
      )}

      {!usable && (
        <p className="fo-template-refusal" id={refusalId}>
          {t('tasklib.card.notApproved')}
        </p>
      )}

      <footer className="fo-template-actions">
        <button
          type="button"
          className="ui-button ui-button-primary fo-template-use"
          onClick={onUse}
          disabled={busy || !usable}

          // Said in the card rather than in a `title`. A native tooltip on a *disabled*
          // button is reachable by nobody: disabled controls are skipped by keyboard
          // navigation, are not reliably announced, and a touch user never hovers -- so the
          // only explanation of why a draft cannot be used was invisible to everyone not
          // using a mouse. It was also the one place this product declined without saying why.
          aria-describedby={usable ? undefined : refusalId}
        >
          {t('tasklib.card.use')}
        </button>
        <IconButton icon="copy" label={t('tasklib.card.copy')} onClick={onCopy} disabled={busy} />
        <IconButton icon="edit" label={t('tasklib.card.edit')} onClick={onEdit} disabled={busy} />
        {mayRetire && template.status !== 'RETIRED' && (
          <IconButton
            icon="archive"
            label={t('tasklib.card.retire')}
            onClick={onRetire}
            disabled={busy}
          />
        )}
      </footer>
    </article>
  );
}
