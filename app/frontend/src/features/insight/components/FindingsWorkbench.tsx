import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { QueuedFinding } from '../api/analyserApi';
import { dismissalFor } from '../dismissalWording';
import { useDismissFinding, useFindingQueue, useLatestAnalysis } from '../hooks/useAnalysis';
import { FindingEvidence } from './FindingEvidence';
import '../pipeline-board.css';

export function FindingsWorkbench({
  onOpenGraph,
  onOpenMessage,
}: {
  onOpenGraph?: (conversationId: string) => void;
  onOpenMessage?: (conversationId: string, messageId: string) => void;
} = {}): JSX.Element {
  const { t } = useTranslation();
  const queue = useFindingQueue();
  const analysis = useLatestAnalysis();
  const dismiss = useDismissFinding();

  const [chosen, setChosen] = useState<string | undefined>(undefined);

  const groups = queue.data?.groups ?? [];
  const standing = queue.data?.standing;
  const all = groups.flatMap((group) => group.items);
  const finding = all.find((item) => item.id === chosen);

  return (
    <div className="fo-pb">
      <div className="fo-fw-queue">
        <section className="fo-pb-ins">
          <span className="fo-pb-eyebrow">{t('findings.sinceTheRunBefore')}</span>

          <div className="fo-fw-standing">
            <Counter label={t('findings.worse')} n={standing?.worsening ?? 0} />
            <Counter label={t('findings.new')} n={standing?.fresh ?? 0} />
            <Counter label={t('findings.stillTrue')} n={standing?.stillTrue ?? 0} loud />
            <Counter label={t('findings.better')} n={standing?.improving ?? 0} />
          </div>

          <p className="fo-pb-note">
            {standing?.nothingNew === true
              ? t('findings.nothingNew', { count: standing.stillTrue })
              : t('findings.somethingMoved', { count: standing?.shown ?? 0 })}
          </p>
        </section>

        {groups.map((group) => (
          <div className="fo-pb-stack" key={group.category}>
            <div className="fo-fw-grouphead">
              <span className="fo-pb-eyebrow">{categoryWord(group.category, t)}</span>
              <span className="fo-fw-rule" />
              <span className="fo-pb-mono fo-pb-faint-ink">{group.items.length}</span>
            </div>

            {group.items.map((item) => (
              <button
                type="button"
                key={item.id}
                className={item.id === chosen ? 'fo-fw-card fo-fw-card--on' : 'fo-fw-card'}
                aria-pressed={item.id === chosen}
                onClick={() => {
                  setChosen((was) => (was === item.id ? undefined : item.id));
                }}
              >
                {item.id === chosen ? <span className="fo-fw-arc" aria-hidden="true" /> : null}

                <span className="fo-pb-between">
                  <span className="fo-pb-mono fo-pb-faint-ink">
                    {item.analyser} · {item.subjectName ?? item.subject}
                  </span>
                </span>

                <span className="fo-fw-card-title">{item.headline}</span>

                <span className="fo-pb-chips">
                  <span className="fo-fw-life">{lifecycleWord(item.lifecycle, t)}</span>
                  <span className="fo-pb-mono fo-pb-faint-ink">{touches(item, t)}</span>
                </span>

                <span className="fo-pb-floor">
                  <span style={{ width: reachShare(item), background: 'var(--data-4)' }} />
                </span>
              </button>
            ))}
          </div>
        ))}

        {queue.isPending ? <p className="fo-pb-quiet">{t('findings.loading')}</p> : null}
        {!queue.isPending && all.length === 0 ? (
          <p className="fo-pb-quiet">{t('findings.empty')}</p>
        ) : null}
      </div>

      <div className="fo-fw-detail">
        {finding === undefined ? (
          <section className="fo-fw-idle">
            <span className="fo-fw-idle-tile" aria-hidden="true">
              ◈
            </span>
            <h2 className="fo-fw-idle-title">{t('findings.pickSomething')}</h2>
            <p className="fo-fw-idle-lede">{t('findings.pickSomethingLede')}</p>
            {all.length > 0 ? (
              <button
                type="button"
                className="ui-button ui-button-primary"
                onClick={() => {
                  setChosen(all[0]?.id);
                }}
              >
                {t('findings.openTheFirst')}
              </button>
            ) : null}
          </section>
        ) : (
          <section className="fo-fw-card-open">
            <div className="fo-pb-between fo-pb-between--top">
              <div className="fo-fw-head">
                <span className="fo-fw-head-tile" aria-hidden="true">
                  ◧
                </span>
                <div className="fo-pb-stack fo-pb-stack--tight">
                  <span className="fo-pb-eyebrow">
                    {finding.analyser} · {finding.subjectName ?? finding.subject}
                  </span>
                  <h2 className="fo-fw-head-title">{finding.headline}</h2>
                </div>
              </div>
              <span className="fo-fw-life">{lifecycleWord(finding.lifecycle, t)}</span>
            </div>

            <div className="fo-fw-stats">
              <Stat
                label={t('findings.touches')}
                value={String(finding.reach)}
                note={
                  finding.reachOf === null
                    ? t('findings.noDenominator')
                    : t('findings.ofN', { count: finding.reachOf })
                }
              />
              <Stat
                label={t('findings.share')}
                value={reachShare(finding)}
                note={t('findings.ofWhatWasRead')}
              />
              <Stat
                label={t('findings.seenIn')}
                value={String(finding.timesSeen)}
                note={t('findings.consecutiveRuns')}
              />
              <Stat
                label={t('findings.confidence')}
                value={finding.confidence ?? '—'}
                note={finding.severity ?? t('findings.noSeverity')}
              />
            </div>

            <Panel title={t('findings.whatWasFound')}>
              <p className="fo-fw-found">{finding.headline}</p>
              {finding.because.map((line) => (
                <p className="fo-fw-because" key={line}>
                  {line}
                </p>
              ))}
            </Panel>

            <Panel
              title={t('findings.whyItRanks')}
              aside={t('findings.score', { score: finding.priority.toFixed(2) })}
            >
              <p className="fo-fw-because">{finding.whyItRanks}</p>
            </Panel>

            <FindingEvidence
              findingId={finding.id}
              onOpenGraph={onOpenGraph}
              onOpenMessage={onOpenMessage}
            />

            <Panel title={t('findings.whatYouMightDo')}>
              <div className="fo-fw-action">
                <span className="fo-fw-action-n">1</span>
                <span className="fo-pb-stack fo-pb-stack--tight">
                  <span className="fo-fw-action-title">{finding.action}</span>
                  {finding.context === null ? null : (
                    <span className="fo-fw-because">{contextLine(finding, t)}</span>
                  )}
                </span>
              </div>
            </Panel>

            <div className="fo-fw-footer">
              <span className="fo-fw-primary">{finding.action}</span>
              <button
                type="button"
                className="ui-button ui-button-secondary"
                disabled={dismiss.isPending}
                onClick={() => {
                  dismiss.mutate(finding.id);
                  setChosen(undefined);
                }}
              >
                {t(dismissalFor(finding.kind))}
              </button>
              <span className="fo-pb-grow" />
              <span className="fo-pb-mono fo-pb-faint-ink">
                {t('findings.firstSeen', {
                  when: finding.firstSeenAt.slice(0, 16).replace('T', ' '),
                })}
              </span>
            </div>
          </section>
        )}
      </div>

      <aside className="fo-fw-rail">
        <section className="fo-pb-ins">
          <span className="fo-pb-eyebrow">{t('findings.history')}</span>

          {finding === undefined ? (
            <p className="fo-pb-lede">{t('findings.historyIdle')}</p>
          ) : (
            <>
              <div className="fo-pb-figure-row">
                <span className="fo-pb-figure">{finding.reach}</span>
                <span className="fo-pb-eyebrow">{t('findings.thingsTouched')}</span>
              </div>

              <p className="fo-pb-lede">
                {t('findings.heldSince', {
                  count: finding.timesSeen,
                  when: finding.firstSeenAt.slice(0, 10),
                })}
              </p>
            </>
          )}
        </section>

        <section className="fo-pb-ins">
          <span className="fo-pb-eyebrow">{t('findings.whereTheyComeFrom')}</span>
          <div className="fo-pb-stack fo-pb-stack--tight">
            <p className="fo-pb-legend">
              <span className="fo-pb-swatch" style={{ background: 'var(--positive)' }} />
              <span className="fo-pb-legend-label">{t('findings.workingAnalysers')}</span>
              <span className="fo-pb-legend-n">
                {producing(analysis.data?.analysers ?? []).working}
              </span>
            </p>
            <p className="fo-pb-legend">
              <span className="fo-pb-swatch" style={{ background: 'var(--warning)' }} />
              <span className="fo-pb-legend-label">{t('findings.blockedCouldAdd')}</span>
              <span className="fo-pb-legend-n">
                {producing(analysis.data?.analysers ?? []).blocked}
              </span>
            </p>
            <p className="fo-pb-legend">
              <span className="fo-pb-swatch" style={{ background: 'var(--line-strong)' }} />
              <span className="fo-pb-legend-label">{t('findings.ranSaidNothing')}</span>
              <span className="fo-pb-legend-n">
                {producing(analysis.data?.analysers ?? []).quiet}
              </span>
            </p>
          </div>
        </section>
      </aside>
    </div>
  );
}

function Counter({
  label,
  n,
  loud = false,
}: {
  label: string;
  n: number;
  loud?: boolean;
}): JSX.Element {
  return (
    <div className={loud ? 'fo-fw-counter fo-fw-counter--loud' : 'fo-fw-counter'}>
      <span className="fo-pb-eyebrow">{label}</span>
      <span className="fo-fw-counter-n">{n}</span>
    </div>
  );
}

function Stat({ label, value, note }: { label: string; value: string; note: string }): JSX.Element {
  return (
    <div className="fo-fw-stat">
      <span className="fo-pb-eyebrow">{label}</span>
      <span className="fo-fw-stat-n">{value}</span>
      <span className="fo-pb-mono fo-pb-faint-ink">{note}</span>
    </div>
  );
}

function Panel({
  title,
  aside,
  children,
}: {
  title: string;
  aside?: string;
  children: React.ReactNode;
}): JSX.Element {
  return (
    <section className="fo-fw-panel">
      <div className="fo-pb-between">
        <span className="fo-pb-eyebrow">{title}</span>
        {aside === undefined ? null : <span className="fo-pb-mono fo-pb-faint-ink">{aside}</span>}
      </div>
      {children}
    </section>
  );
}

type Translate = (key: string, vars?: Record<string, unknown>) => string;

function categoryWord(category: string, t: Translate): string {
  const known = ['YOUR_WORK', 'YOUR_LIBRARY', 'YOUR_PROCESSES', 'YOUR_ENGAGEMENTS', 'YOUR_TIME'];
  return known.includes(category) ? t(`findings.category.${category}`) : category;
}

function lifecycleWord(lifecycle: string, t: Translate): string {
  const known = ['NEW', 'STILL_TRUE', 'WORSENING', 'IMPROVING', 'RESOLVED'];
  return known.includes(lifecycle) ? t(`findings.life.${lifecycle}`) : lifecycle;
}

function touches(item: QueuedFinding, t: Translate): string {
  return item.reachOf === null
    ? t('findings.touchesN', { count: item.reach })
    : t('findings.touchesNofM', { count: item.reach, of: item.reachOf });
}

function reachShare(item: QueuedFinding): string {
  if (item.reachOf === null || item.reachOf <= 0) {
    return '0%';
  }
  return `${String(Math.min(100, Math.round((item.reach / item.reachOf) * 100)))}%`;
}

function contextLine(item: QueuedFinding, t: Translate): string {
  const context = item.context;
  if (context === null) {
    return '';
  }
  const parts: string[] = [];
  if (context.clients.length > 0) {
    parts.push(t('findings.ctxClients', { list: context.clients.join(', ') }));
  }
  if (context.workTypes.length > 0) {
    parts.push(t('findings.ctxWorkTypes', { list: context.workTypes.join(', ') }));
  }
  if (context.engagements > 0) {
    parts.push(t('findings.ctxEngagements', { count: context.engagements }));
  }
  return parts.join(' · ');
}

function producing(
  analysers: readonly { findings: number; preconditions: readonly { met: boolean }[] }[],
): { working: number; blocked: number; quiet: number } {
  let working = 0;
  let blocked = 0;
  let quiet = 0;
  for (const line of analysers) {
    if (line.preconditions.some((pre) => !pre.met)) {
      blocked += 1;
    } else if (line.findings > 0) {
      working += 1;
    } else {
      quiet += 1;
    }
  }
  return { working, blocked, quiet };
}
