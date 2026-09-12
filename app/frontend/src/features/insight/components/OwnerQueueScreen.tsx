import { useMemo, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { FindingQueue, QueuedFinding } from '../api/analyserApi';
import { useDismissFinding, useFindingQueue } from '../hooks/useAnalysis';
import { FindingContextLine } from './FindingContextLine';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { Inspector } from '../../../shared/ui/Inspector';
import { Spinner } from '../../../shared/ui/Spinner';
import { WorkbenchPage } from '../../../shared/ui/WorkbenchPage';

export function OwnerQueueScreen(): JSX.Element {
  const { t } = useTranslation();
  const queue = useFindingQueue();
  const dismiss = useDismissFinding();

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [filter, setFilter] = useState<Lifecycle | null>(null);
  const [inspectorOpen, setInspectorOpen] = useState(true);

  const groups = useMemo(() => filtered(queue.data, filter), [queue.data, filter]);
  const selected = useMemo(() => findIn(groups, selectedId), [groups, selectedId]);

  if (queue.isPending) {
    return (
      <div className="fo-queue__state">
        <Spinner label={t('queue.loading')} />
      </div>
    );
  }

  if (queue.isError) {
    return (
      <div className="fo-queue__state">
        <EmptyState heading={t('queue.failed.title')} body={t('queue.failed.body')} />
      </div>
    );
  }

  if (queue.data === null || queue.data === undefined) {
    return (
      <div className="fo-queue__state">
        <EmptyState heading={t('queue.never.title')} body={t('queue.never.body')} />
      </div>
    );
  }

  const standing = queue.data.standing;

  return (
    <WorkbenchPage
      listLabel={t('queue.listLabel')}
      list={
        <div className="fo-queue">
          <StandingFilters standing={standing} active={filter} onPick={setFilter} />

          {standing.nothingNew ? (
            <p className="fo-queue__quiet">
              {t('queue.nothingNew', { count: standing.stillTrue })}
            </p>
          ) : null}

          {groups.length === 0 ? (
            <p className="fo-queue__none">{t('queue.none')}</p>
          ) : (
            groups.map((group) => (
              <section className="fo-queue__group" key={group.category}>
                <h3 className="fo-queue__group-label">{t(`queue.category.${group.category}`)}</h3>
                <ul className="fo-queue__rows">
                  {group.items.map((item) => (
                    <QueueRow
                      key={item.id}
                      item={item}
                      selected={item.id === selectedId}
                      onSelect={() => {
                        setSelectedId(item.id);
                      }}
                    />
                  ))}
                </ul>
              </section>
            ))
          )}
        </div>
      }
      focus={
        selected === null ? (
          <EmptyState heading={t('queue.pick.title')} body={t('queue.pick.body')} />
        ) : (
          <FindingDetail
            finding={selected}
            dismissing={dismiss.isPending}
            onDismiss={() => {
              dismiss.mutate(selected.id, {
                onSuccess: () => {
                  setSelectedId(null);
                },
              });
            }}
          />
        )
      }
      inspector={
        <Inspector
          open={inspectorOpen}
          onToggle={() => {
            setInspectorOpen((open) => !open);
          }}
          label={t('queue.inspector.label')}
          empty={<p className="fo-queue__hint">{t('queue.inspector.empty')}</p>}
        >
          {selected === null ? undefined : <FindingContext finding={selected} />}
        </Inspector>
      }
    />
  );
}

type Lifecycle = 'NEW' | 'WORSENING' | 'STILL_TRUE' | 'IMPROVING';

function StandingFilters({
  standing,
  active,
  onPick,
}: {
  readonly standing: FindingQueue['standing'];
  readonly active: Lifecycle | null;
  readonly onPick: (next: Lifecycle | null) => void;
}): JSX.Element {
  const { t } = useTranslation();

  const counts: readonly { readonly life: Lifecycle; readonly value: number }[] = [
    { life: 'WORSENING', value: standing.worsening },
    { life: 'NEW', value: standing.fresh },
    { life: 'STILL_TRUE', value: standing.stillTrue },
    { life: 'IMPROVING', value: standing.improving },
  ];

  return (
    <div className="fo-queue__standing" role="group" aria-label={t('queue.standing.label')}>
      {counts.map(({ life, value }) => (
        <button
          type="button"
          key={life}
          className="fo-queue__count"
          aria-pressed={active === life}
          onClick={() => {
            onPick(active === life ? null : life);
          }}
        >
          <span className="fo-queue__count-label">{t(`queue.lifecycle.${life}`)}</span>
          <span className="fo-queue__count-value">{value}</span>
        </button>
      ))}
      {standing.dismissed > 0 ? (
        <p className="fo-queue__held">{t('queue.held', { count: standing.dismissed })}</p>
      ) : null}
    </div>
  );
}

function QueueRow({
  item,
  selected,
  onSelect,
}: {
  readonly item: QueuedFinding;
  readonly selected: boolean;
  readonly onSelect: () => void;
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <li>
      <button
        type="button"
        className="fo-queue__row"
        aria-current={selected ? 'true' : undefined}
        onClick={onSelect}
      >
        <span className="fo-queue__row-eyebrow">
          {item.subjectName === null ? item.analyser : `${item.analyser} · ${item.subjectName}`}
        </span>
        <span className="fo-queue__row-title">{item.headline}</span>
        <span className="fo-queue__row-meta">
          <span className="fo-queue__pill" data-lifecycle={item.lifecycle}>
            {t(`queue.lifecycle.${item.lifecycle}`)}
          </span>
          <span className="fo-queue__reach">
            {item.reachOf === null
              ? t('queue.reach.bare', { count: item.reach })
              : t('queue.reach.of', { reach: item.reach, of: item.reachOf })}
          </span>
        </span>
      </button>
    </li>
  );
}

function FindingDetail({
  finding,
  dismissing,
  onDismiss,
}: {
  readonly finding: QueuedFinding;
  readonly dismissing: boolean;
  readonly onDismiss: () => void;
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <article className="fo-finding" aria-labelledby="fo-finding-title">
      <p className="fo-finding__eyebrow">
        {finding.subjectName === null
          ? finding.analyser
          : `${finding.analyser} · ${finding.subjectName}`}
      </p>
      <h2 className="fo-finding__title" id="fo-finding-title">
        {finding.headline}
      </h2>
      <p className="fo-finding__why">{finding.whyItRanks}</p>

      <FindingContextLine context={finding.context} />

      <ul className="fo-finding__because">
        {finding.because.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>

      <p className="fo-finding__verb">
        <span className="fo-finding__verb-label">{t('queue.suggested')}</span> {finding.action}
      </p>

      <div className="fo-finding__actions">
        <button
          type="button"
          className="ui-button ui-button-secondary"
          onClick={onDismiss}
          disabled={dismissing}
        >
          {dismissing ? t('queue.dismissing') : t('queue.dismiss')}
        </button>
        <p className="fo-finding__dismiss-note">{t('queue.dismissNote')}</p>
      </div>
    </article>
  );
}

function FindingContext({ finding }: { readonly finding: QueuedFinding }): JSX.Element {
  const { t } = useTranslation();

  return (
    <>
      <section className="fo-inspector-card">
        <h3 className="fo-inspector-title">{t('queue.context.standing')}</h3>
        <dl className="fo-queue__facts">
          <dt>{t('queue.context.lifecycle')}</dt>
          <dd>{t(`queue.lifecycle.${finding.lifecycle}`)}</dd>
          <dt>{t('queue.context.timesSeen')}</dt>
          <dd>{finding.timesSeen}</dd>
          <dt>{t('queue.context.firstSeen')}</dt>
          <dd>{new Date(finding.firstSeenAt).toLocaleDateString()}</dd>
        </dl>
      </section>

      <section className="fo-inspector-card">
        <h3 className="fo-inspector-title">{t('queue.context.measure')}</h3>
        <dl className="fo-queue__facts">
          <dt>{t('queue.context.reach')}</dt>
          <dd>
            {finding.reachOf === null
              ? t('queue.reach.bare', { count: finding.reach })
              : t('queue.reach.of', { reach: finding.reach, of: finding.reachOf })}
          </dd>
          <dt>{t('queue.context.severity')}</dt>
          <dd>{finding.severity ?? t('queue.context.unstated')}</dd>
          <dt>{t('queue.context.confidence')}</dt>
          <dd>{finding.confidence ?? t('queue.context.unstated')}</dd>
        </dl>
      </section>

      {finding.evidence.length === 0 ? null : (
        <section className="fo-inspector-card">
          <h3 className="fo-inspector-title">{t('queue.context.evidence')}</h3>
          <p className="fo-inspector-detail">
            {t('queue.context.evidenceCount', { count: finding.evidence.length })}
          </p>
        </section>
      )}
    </>
  );
}

function filtered(
  queue: FindingQueue | null | undefined,
  filter: Lifecycle | null,
): readonly FindingQueue['groups'][number][] {
  if (queue === null || queue === undefined) {
    return [];
  }
  if (filter === null) {
    return queue.groups;
  }
  return queue.groups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.lifecycle === filter) }))
    .filter((group) => group.items.length > 0);
}

function findIn(
  groups: readonly FindingQueue['groups'][number][],
  id: string | null,
): QueuedFinding | null {
  if (id === null) {
    return null;
  }
  for (const group of groups) {
    for (const item of group.items) {
      if (item.id === id) {
        return item;
      }
    }
  }
  return null;
}
