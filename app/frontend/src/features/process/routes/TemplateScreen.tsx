import { useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Card } from '../../../shared/ui/Card';
import { DependencyList } from '../../../shared/ui/DependencyList';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { Spinner } from '../../../shared/ui/Spinner';
import type { Candidate, Dependency, Instance, Template, TemplateSummary } from '../api/processApi';
import { AuthorTemplateDialog } from '../components/AuthorTemplateDialog';
import { EditTemplateDialog } from '../components/EditTemplateDialog';
import { StartRunDialog } from '../components/StartRunDialog';
import {
  useDrawDependency,
  useRetireTemplate,
  useEraseDependency,
  useTemplate,
  useTemplates,
} from '../hooks/useProcesses';

interface TemplateScreenProps {
  permissions: readonly string[];

  viewerId: string;

  steerers: readonly Candidate[];

  onStarted: (instance: Instance) => void;

  insightsFor?: (templateId: string) => ReactNode;
}

export function TemplateScreen({
  permissions,
  viewerId,
  steerers,
  onStarted,
  insightsFor,
}: TemplateScreenProps): JSX.Element {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<string | null>(null);
  const [authoring, setAuthoring] = useState(false);
  const [editing, setEditing] = useState(false);
  const [starting, setStarting] = useState<Template | null>(null);
  const templates = useTemplates();
  const template = useTemplate(selected);

  const mayAuthor = permissions.includes('PROCESS_TEMPLATE_AUTHOR');
  const mayStart = permissions.includes('PROCESS_INSTANTIATE');

  if (templates.isPending) {
    return <Spinner label={t('process.loading')} />;
  }

  if (templates.isError) {
    return <Banner tone="alert">{t('process.loadFailed')}</Banner>;
  }

  const library = templates.data?.templates ?? [];

  return (
    <section aria-label={t('process.section.templates')}>
      <header
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 'var(--space-4)',
          marginBottom: 'var(--space-4)',
        }}
      >
        <h2
          style={{
            margin: 0,
            fontSize: 'var(--text-lg)',
            fontWeight: 600,
            letterSpacing: '-0.01em',
          }}
        >
          {t('process.section.templates')}
        </h2>
        {mayAuthor ? (
          <Button
            onClick={() => {
              setAuthoring(true);
            }}
          >
            {t('process.author.title')}
          </Button>
        ) : null}
      </header>

      {library.length === 0 ? (
        <EmptyState heading={t('process.empty.heading')} body={t('process.empty.body')} />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(220px, 320px) 1fr',
            gap: 'var(--space-5)',
            alignItems: 'start',
          }}
        >
          <TemplateList library={library} selected={selected} onSelect={setSelected} />
          {template.isError ? <Banner tone="alert">{t('process.loadFailed')}</Banner> : null}
          {template.data ? (
            <TemplateDetail
              template={template.data}
              insights={insightsFor?.(template.data.id)}
              mayStart={mayStart}
              mayEdit={mayEdit(permissions, viewerId, template.data)}

              mayRetire={
                permissions.includes('PROCESS_TEMPLATE_RETIRE') &&
                (template.data.authorId === viewerId || permissions.includes('PROCESS_VIEW_ANY'))
              }
              onStart={() => {
                setStarting(template.data);
              }}
              onEdit={() => {
                setEditing(true);
              }}
            />
          ) : null}
        </div>
      )}

      <AuthorTemplateDialog
        open={authoring}
        onClose={() => {
          setAuthoring(false);
        }}
        onAuthored={(created) => {
          setSelected(created.id);
          setAuthoring(false);
        }}
      />

      {template.data ? (
        <EditTemplateDialog
          key={template.data.id}
          open={editing}
          template={template.data}
          onClose={() => {
            setEditing(false);
          }}
          onSaved={() => {
            setEditing(false);
          }}
        />
      ) : null}

      <StartRunDialog
        template={starting}
        steerers={steerers}
        onClose={() => {
          setStarting(null);
        }}
        onStarted={onStarted}
      />
    </section>
  );
}

function mayEdit(permissions: readonly string[], viewerId: string, template: Template): boolean {
  return (
    permissions.includes('PROCESS_TEMPLATE_EDIT') &&
    (template.authorId === viewerId || permissions.includes('PROCESS_VIEW_ANY'))
  );
}

function TemplateList({
  library,
  selected,
  onSelect,
}: {
  library: TemplateSummary[];
  selected: string | null;
  onSelect: (id: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  return (
    <ul
      aria-label={t('process.section.library')}
      style={{
        listStyle: 'none',
        margin: 0,
        padding: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-2)',
      }}
    >
      {library.map((entry) => (
        <li key={entry.id}>
          <button
            type="button"
            aria-current={entry.id === selected}
            onClick={() => {
              onSelect(entry.id);
            }}
            style={{
              width: '100%',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'flex-start',
              gap: 'var(--space-1)',
              padding: 'var(--space-3) var(--space-4)',
              borderRadius: 'var(--radius-card)',
              border: `1px solid ${entry.id === selected ? 'var(--brand)' : 'var(--line)'}`,
              background: entry.id === selected ? 'var(--brand-soft)' : 'var(--surface)',
              color: 'var(--ink)',
              textAlign: 'start',
              cursor: 'pointer',
            }}
          >
            <span style={{ fontWeight: 600, fontSize: 'var(--text-base)' }}>{entry.name}</span>

            <span style={{ color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
              {t('process.template.stepCount', { count: entry.stepCount })}
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

function TemplateDetail({
  template,
  mayStart,
  mayEdit,
  mayRetire,
  insights,
  onStart,
  onEdit,
}: {
  template: Template;
  mayStart: boolean;
  mayEdit: boolean;

  mayRetire: boolean;

  insights?: ReactNode;
  onStart: () => void;
  onEdit: () => void;
}): JSX.Element {
  const { t } = useTranslation();
  const draw = useDrawDependency(template.id);
  const erase = useEraseDependency(template.id);
  const retire = useRetireTemplate(template.id);

  const failure = draw.error ?? erase.error;
  const refusal = failure instanceof ApiError ? failure : undefined;
  const inCycle =
    refusal?.code === 'GRAPH_CYCLE' ? refusal.details.map((violation) => violation.field) : [];

  const code = refusal?.code ?? (failure === null || failure === undefined ? undefined : 'UNKNOWN');

  function change(edge: Dependency, how: typeof draw | typeof erase): void {
    draw.reset();
    erase.reset();
    how.mutate(edge);
  }

  return (
    <Card>
      <h3 style={{ margin: 0, fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--ink)' }}>
        {template.name}
      </h3>
      {template.overview === null ? null : (
        <p
          style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-md)', lineHeight: 1.6 }}
        >
          {template.overview}
        </p>
      )}

      {insights}

      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.dependency.error.${code}`, t('process.dependency.error.UNKNOWN'))}
        </Banner>
      )}

      <DependencyList
        steps={template.steps.map((step) => ({
          id: step.id,
          title: step.title ?? step.taskTemplateId,
        }))}
        edges={template.dependencies}
        inCycle={inCycle}
        disabled={draw.isPending || erase.isPending}
        labels={{
          waitsFor: t('process.step.waitsFor'),
          waitsForNothing: t('process.step.waitsForNothing'),
          add: t('process.dependency.add'),
          remove: t('process.dependency.remove'),
        }}
        onAdd={(edge) => {
          change(edge, draw);
        }}
        onRemove={(edge) => {
          change(edge, erase);
        }}
      />

      {!template.active ? (
        <Banner tone="waiting">{t('process.retire.retired')}</Banner>
      ) : (
        <span style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          {mayStart ? <Button onClick={onStart}>{t('process.start.title')}</Button> : null}
          {mayEdit ? (
            <Button variant="quiet" onClick={onEdit}>
              {t('process.edit.open')}
            </Button>
          ) : null}
          {mayRetire ? (
            <Button
              variant="quiet"
              loading={retire.isPending}
              loadingLabel={t('process.retire.submitting')}
              onClick={() => {
                retire.mutate();
              }}
            >
              {t('process.retire.action')}
            </Button>
          ) : null}
        </span>
      )}
    </Card>
  );
}
