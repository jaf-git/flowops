import { useQuery } from '@tanstack/react-query';
import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';

import {
  fetchFindingEvidence,
  type EvidenceNode,
  type EvidencePerson,
} from '../api/findingEvidenceApi';

export function FindingEvidence({
  findingId,
  onOpenGraph,
  onOpenMessage,
}: {
  findingId: string;

  onOpenGraph?: (conversationId: string) => void;
  onOpenMessage?: (conversationId: string, messageId: string) => void;
}): JSX.Element {
  const { t, i18n } = useTranslation();

  const evidence = useQuery({
    queryKey: ['analysis', 'findings', findingId, 'evidence'],
    queryFn: () => fetchFindingEvidence(findingId),
  });

  if (evidence.isPending) {
    return <p className="fo-fw-foot">{t('evidence.loading')}</p>;
  }

  if (evidence.isError || evidence.data === undefined) {
    return <p className="fo-fw-foot">{t('evidence.failed')}</p>;
  }

  const { nodes, templates, jobs, brackets, waits, spread } = evidence.data;

  return (
    <div className="fo-ev">
      {spread.departments.length > 0 ? (
        <div
          className={
            spread.crossesDepartments ? 'fo-ev-spread fo-ev-spread--across' : 'fo-ev-spread'
          }
        >
          <span className="fo-pb-eyebrow">{t('evidence.reach')}</span>
          <p className="fo-ev-spread-line">
            {spread.crossesDepartments
              ? t('evidence.crosses', {
                  count: spread.departments.length,
                  list: spread.departments.join(', '),
                })
              : t('evidence.within', { department: spread.departments[0] ?? '' })}
          </p>
          {spread.roles.length > 0 ? (
            <p className="fo-fw-foot">{t('evidence.roles', { list: spread.roles.join(', ') })}</p>
          ) : null}
        </div>
      ) : null}

      {templates.length > 0 ? (
        <section className="fo-fw-panel">
          <span className="fo-pb-eyebrow">{t('evidence.theDraft')}</span>
          {templates.map((template) => (
            <a
              className="fo-ev-template"
              key={template.id}
              href={`/${i18n.language}/templates/${template.id}`}
            >
              <span className="fo-pb-stack fo-pb-stack--tight">
                <span className="fo-fw-action-title">{template.title}</span>
                <span className="fo-pb-mono fo-pb-faint-ink">
                  {[template.status, template.workType, template.responsibleRole]
                    .filter(Boolean)
                    .join(' · ')}
                </span>
                {template.checklist.length > 0 ? (
                  <span className="fo-fw-because">{template.checklist.join(' → ')}</span>
                ) : null}
              </span>
              <span aria-hidden="true">↗</span>
            </a>
          ))}
        </section>
      ) : null}

      {jobs.length > 0 ? (
        <section className="fo-fw-panel">
          <span className="fo-pb-eyebrow">{t('evidence.engagements')}</span>
          {jobs.map((job) => (
            <div className="fo-fw-evidence" key={job.id}>
              <span className="fo-fw-evidence-id">{job.name}</span>
              <span className="fo-pb-mono fo-pb-faint-ink">{job.status}</span>
            </div>
          ))}
        </section>
      ) : null}

      {brackets.length > 0 ? (
        <section className="fo-fw-panel">
          <div className="fo-pb-between">
            <span className="fo-pb-eyebrow">{t('evidence.theWork')}</span>
            <span className="fo-pb-mono fo-pb-faint-ink">
              {t('evidence.nWork', { count: brackets.length })}
            </span>
          </div>
          {brackets.map((bracket) => (
            <div className="fo-fw-evidence" key={bracket.id}>
              <span className="fo-fw-evidence-id">
                {bracket.jobName ?? bracket.projectLabel ?? bracket.id}
              </span>
              <span className="fo-pb-mono fo-pb-faint-ink">
                {[
                  bracket.workType,
                  bracket.closeKind ?? bracket.state,
                  minutesAsWords(bracket.minutes, t),
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </span>
            </div>
          ))}
        </section>
      ) : null}

      {waits.length > 0 ? (
        <section className="fo-fw-panel">
          <div className="fo-pb-between">
            <span className="fo-pb-eyebrow">{t('evidence.theWaits')}</span>
            <span className="fo-pb-mono fo-pb-faint-ink">
              {t('evidence.nWaits', { count: waits.length })}
            </span>
          </div>
          {waits.map((wait) => (
            <div className="fo-fw-evidence" key={wait.id}>
              <span className="fo-fw-evidence-id">{wait.reason ?? wait.jobName ?? wait.id}</span>
              <span className="fo-pb-mono fo-pb-faint-ink">
                {[wait.kind, waitOutcome(wait, t)].filter(Boolean).join(' · ')}
              </span>
            </div>
          ))}
        </section>
      ) : null}

      <section className="fo-fw-panel">
        <div className="fo-pb-between">
          <span className="fo-pb-eyebrow">{t('evidence.theMarks')}</span>
          <span className="fo-pb-mono fo-pb-faint-ink">
            {t('evidence.nMarks', { count: nodes.length })}
          </span>
        </div>

        {nodes.length === 0 ? (
          <p className="fo-fw-foot">{t('evidence.noNodes')}</p>
        ) : (
          nodes.map((node) => (
            <NodeCard
              key={node.id}
              node={node}
              onOpenGraph={onOpenGraph}
              onOpenMessage={onOpenMessage}
            />
          ))
        )}

        <p className="fo-fw-foot">{t('evidence.foot')}</p>
      </section>
    </div>
  );
}

function NodeCard({
  node,
  onOpenGraph,
  onOpenMessage,
}: {
  node: EvidenceNode;
  onOpenGraph?: (conversationId: string) => void;
  onOpenMessage?: (conversationId: string, messageId: string) => void;
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <article className="fo-ev-node">
      <header className="fo-pb-between fo-pb-between--top">
        <span className="fo-pb-chips">
          {node.workType === null ? null : <span className="fo-ev-type">{node.workType}</span>}

          {node.nodeRole === null ? null : (
            <span className="fo-pb-mono fo-pb-faint-ink">{node.nodeRole}</span>
          )}
          {node.jobName === null ? null : (
            <span className="fo-pb-mono fo-pb-faint-ink">{node.jobName}</span>
          )}
        </span>

        <span className="fo-pb-chips">
          {node.conversationId !== null && onOpenGraph !== undefined ? (
            <button
              type="button"
              className="fo-ev-link"
              onClick={() => {
                onOpenGraph(node.conversationId as string);
              }}
            >
              {t('evidence.openGraph')} <span aria-hidden="true">↗</span>
            </button>
          ) : null}
          {node.conversationId !== null &&
          node.messageId !== null &&
          onOpenMessage !== undefined ? (
            <button
              type="button"
              className="fo-ev-link"
              onClick={() => {
                onOpenMessage(node.conversationId as string, node.messageId as string);
              }}
            >
              {t('evidence.openMessage')} <span aria-hidden="true">↗</span>
            </button>
          ) : null}
        </span>
      </header>

      {node.title === null ? null : <p className="fo-ev-title">{node.title}</p>}
      {node.detail === null ? null : <p className="fo-fw-because">{node.detail}</p>}

      {node.messageText === null ? (
        <p className="fo-fw-foot">{t('evidence.noMessage')}</p>
      ) : (
        <blockquote className="fo-ev-quote">{node.messageText}</blockquote>
      )}

      {node.checklist === null ? (
        <p className="fo-fw-foot">{t('evidence.noChecklistAsked')}</p>
      ) : node.checklist.length === 0 ? (
        <p className="fo-fw-foot">{t('evidence.noChecklistDeliberately')}</p>
      ) : (
        <ol className="fo-ev-steps">
          {node.checklist.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      )}

      <footer className="fo-ev-people">
        <Who label={t('evidence.markedBy')} person={node.marker} />
        <Who label={t('evidence.doneBy')} person={node.performer} />
      </footer>
    </article>
  );
}

function Who({ label, person }: { label: string; person: EvidencePerson }): JSX.Element {
  const { t } = useTranslation();

  if (person.name === null) {
    return (
      <span className="fo-ev-who">
        <span className="fo-pb-eyebrow">{label}</span>
        <span className="fo-fw-foot">{t('evidence.notRecorded')}</span>
      </span>
    );
  }

  return (
    <span className="fo-ev-who">
      <span className="fo-pb-eyebrow">{label}</span>
      <span className="fo-ev-who-name">{person.name}</span>
      <span className="fo-pb-mono fo-pb-faint-ink">
        {[person.role, person.department].filter(Boolean).join(' · ') ||
          t('evidence.roleNotRecorded')}
      </span>
    </span>
  );
}

function minutesAsWords(minutes: number | null, t: TFunction): string {
  if (minutes === null) {
    return t('evidence.stillOpen');
  }
  if (minutes < 60) {
    return t('evidence.minutes', { count: minutes });
  }
  return t('evidence.hours', { count: Math.round(minutes / 60) });
}

function waitOutcome(
  wait: { satisfiedAt: string | null; cancelledAt: string | null; days: number | null },
  t: TFunction,
): string {
  if (wait.satisfiedAt !== null) {
    return t('evidence.waitedDays', { count: wait.days ?? 0 });
  }
  return wait.cancelledAt !== null ? t('evidence.withdrawn') : t('evidence.neverAnswered');
}
