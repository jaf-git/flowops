import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { StatTile } from '../../../shared/ui/StatTile';
import { describeEnding, type CloseKind } from '../api/bracketApi';
import { useConversationWaits, useConversationWork } from '../hooks/useConversationWork';

interface AnalyticsTabProps {
  readonly conversationId: string;
}

export function AnalyticsTab({ conversationId }: AnalyticsTabProps): JSX.Element {
  const { t } = useTranslation();
  const work = useConversationWork(conversationId);
  const waits = useConversationWaits(conversationId);

  if (work.isPending) {
    return <p className="fo-work-quiet">{t('discovery.analytics.loading')}</p>;
  }

  if (work.isError) {
    return <p className="fo-work-quiet">{t('discovery.analytics.failed')}</p>;
  }

  const brackets = work.data ?? [];

  if (brackets.length === 0) {
    return (
      <div className="fo-work-empty">
        <p>{t('discovery.analytics.empty.what')}</p>
        <p className="fo-work-quiet">{t('discovery.analytics.empty.how')}</p>
      </div>
    );
  }

  const ended = brackets.filter((bracket) => bracket.closeKind !== null);
  const arrived = ended.filter(
    (bracket) => describeEnding(bracket.closeKind as CloseKind).completed,
  );

  const otherwise = new Map<string, number>();
  for (const bracket of ended) {
    const how = describeEnding(bracket.closeKind as CloseKind);
    if (!how.completed) {
      otherwise.set(how.label, (otherwise.get(how.label) ?? 0) + 1);
    }
  }

  const open = waits.data ?? [];
  const outside = open.filter((wait) => wait.external);

  const byType = new Map<string, number>();
  for (const bracket of brackets) {
    byType.set(bracket.workType, (byType.get(bracket.workType) ?? 0) + 1);
  }

  const kinds = [...byType.entries()].sort((a, b) =>
    b[1] === a[1] ? a[0].localeCompare(b[0]) : b[1] - a[1],
  );

  return (
    <div className="fo-analytics">
      <div className="fo-stat-grid">
        <StatTile
          label={t('discovery.analytics.endings.label')}
          value={String(arrived.length)}

          sub={t('discovery.analytics.endings.sub', {
            ended: ended.length,
            live: brackets.length - ended.length,
          })}
        />
        <StatTile
          label={t('discovery.analytics.waiting.label')}
          value={String(open.length)}

          sub={
            open.length === 0
              ? t('discovery.analytics.waiting.none')
              : outside.length === 0
                ? t('discovery.analytics.waiting.allInside')
                : t('discovery.analytics.waiting.outside', { count: outside.length })
          }
        />
        <StatTile
          label={t('discovery.analytics.kinds.label')}
          value={String(kinds.length)}

          sub={
            kinds.length === brackets.length
              ? t('discovery.analytics.kinds.everyOneDifferent')
              : t('discovery.analytics.kinds.sub', { count: brackets.length })
          }
        />
      </div>

      {otherwise.size === 0 ? null : (
        <section className="ui-card fo-analytics-block">
          <p className="ui-card-eyebrow">{t('discovery.analytics.endings.eyebrow')}</p>
          <ul className="fo-analytics-list">
            {[...otherwise.entries()].map(([label, count]) => (
              <li key={label} className="fo-analytics-row">
                <span className="fo-analytics-what">{label}</span>
                <span className="fo-analytics-count">{count}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="ui-card fo-analytics-block">
        <p className="ui-card-eyebrow">{t('discovery.analytics.kinds.eyebrow')}</p>
        <ul className="fo-analytics-list">
          {kinds.map(([workType, count]) => (
            <li key={workType} className="fo-analytics-row">
              <span className="fo-analytics-what fo-analytics-type">{workType}</span>
              <span className="fo-analytics-count">{count}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
