import { motion } from 'framer-motion';
import type { JSX, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { SupportedLocale } from '../../../i18n';
import { durationLabel } from '../../../shared/lib/elapsed';
import type { ReportState, ReportSummary } from '../model/summary';
import { PageHeader } from '../../../shared/ui/PageHeader';
import { StatTile } from '../../../shared/ui/StatTile';
import { CountUp } from '../../../shared/motion/CountUp';
import { DURATION, EASE, staggerFor } from '../../../shared/motion/tokens';

const STATE_COLOUR: Record<ReportState, string> = {
  done: 'var(--done)',
  inProgress: 'var(--brand)',
  blocked: 'var(--alert)',
  notStarted: 'var(--faint)',
};

interface ReportsScreenProps {
  report: ReportSummary;
  loading: boolean;

  incomplete?: boolean;

  throughput?: ReactNode;
  locale: SupportedLocale;
}

export function ReportsScreen({
  report,
  loading,
  incomplete = false,
  throughput,
  locale,
}: ReportsScreenProps): JSX.Element {
  const { t } = useTranslation();

  if (loading) {
    return <p style={{ margin: 0, color: 'var(--muted)' }}>{t('reports.loading')}</p>;
  }

  const hours = new Intl.NumberFormat(locale, {
    style: 'unit',
    unit: 'hour',
    unitDisplay: 'narrow',
    maximumFractionDigits: 1,
  });

  return (
    <div className="fo-page">
      <PageHeader title={t('reports.heading')} subtitle={t('reports.subtitle')} />

      {incomplete ? (
        <p
          role="status"
          style={{
            margin: 0,
            padding: 'var(--space-2) var(--space-3)',
            border: '1px solid var(--line)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--muted)',
            fontSize: 'var(--text-sm)',
          }}
        >
          {t('reports.partial')}
        </p>
      ) : null}

      <div className="fo-stat-grid">
        <StatTile
          label={t('reports.tile.activeProcesses')}
          value={<CountUp value={report.activeRuns} />}
          sub={t('reports.tile.ofVisible', { count: report.runs })}
        />
        <StatTile
          label={t('reports.tile.workComplete')}
          value={
            <>
              <CountUp value={report.closed} /> / {report.total}
            </>
          }
          sub={t('reports.tile.completeShare', { percent: report.completionPercent })}
        />
        <StatTile
          label={t('reports.tile.needsAttention')}
          value={<CountUp value={report.needsAttention} />}
          sub={t('reports.tile.attentionSplit', {
            blocked: report.blocked,
            unassigned: report.awaitingAssignment,
          })}
        />
        <StatTile
          label={t('reports.tile.openEstimate')}
          value={<CountUp value={report.openHours} format={(open) => hours.format(open)} />}

          sub={
            report.unestimatedOpen === 0
              ? t('reports.tile.openWorkOnly')
              : `${t('reports.tile.openWorkOnly')} · ${t('reports.tile.unestimated', {
                  count: report.unestimatedOpen,
                })}`
          }
        />
      </div>

      {throughput}

      <div className="fo-report-panels">
        <section className="ui-card fo-panel">
          <h3 className="fo-panel-title">{t('reports.waiting.heading')}</h3>
          {report.waiting.length === 0 ? (
            <p style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
              {t('reports.waiting.empty')}
            </p>
          ) : (
            <ul
              style={{
                listStyle: 'none',
                margin: 0,
                padding: 0,
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-3)',
              }}
            >
              {report.waiting.map((row, index) => (
                <li key={`${row.runId}-${row.stepTitle}`}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'baseline',
                      gap: 'var(--space-2)',
                      marginBlockEnd: 'var(--space-1)',
                    }}
                  >
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: 500 }}>
                      {row.stepTitle}
                    </span>
                    <span style={{ fontSize: 'var(--text-xs)', color: 'var(--faint)' }}>
                      {row.runName}
                    </span>

                    <span
                      style={{
                        marginInlineStart: 'auto',
                        fontSize: 'var(--text-xs)',
                        color: 'var(--waiting)',
                        fontWeight: 500,
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {t('reports.waiting.waited', {
                        duration: durationLabel(row.minutes * 60, locale),
                      })}
                    </span>
                  </div>
                  <Meter share={row.share} colour="var(--waiting)" index={index} />
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="ui-card fo-panel">
          <h3 className="fo-panel-title">{t('reports.distribution.heading')}</h3>
          <ul
            style={{
              listStyle: 'none',
              margin: 0,
              padding: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-3)',
            }}
          >
            {report.distribution.map((row, index) => (
              <li
                key={row.state}
                style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}
              >
                <span
                  aria-hidden="true"
                  style={{
                    width: '8px',
                    height: '8px',
                    borderRadius: '50%',
                    background: STATE_COLOUR[row.state],
                    flexShrink: 0,
                  }}
                />

                <span
                  style={{
                    fontSize: 'var(--text-sm)',
                    color: 'var(--slate)',
                    flex: '0 0 auto',
                    minWidth: '96px',
                  }}
                >
                  {t(`canvas.board.state.${row.state}`)}
                </span>
                <span style={{ flex: 1, minWidth: 0 }}>
                  <Meter share={row.share} colour={STATE_COLOUR[row.state]} index={index} />
                </span>
                <span
                  style={{
                    fontSize: 'var(--text-xs)',
                    fontWeight: 500,
                    minWidth: '20px',
                    textAlign: 'end',
                  }}
                >
                  {row.count}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}

function Meter({
  share,
  colour,
  index = 0,
}: {
  share: number;
  colour: string;
  index?: number;
}): JSX.Element {
  return (
    <span className="fo-meter" aria-hidden="true">
      <motion.span
        className="fo-meter-fill"
        style={{
          width: `${Math.round(share * 100)}%`,
          background: colour,
          originX: 0,
        }}
        initial={{ scaleX: 0 }}
        animate={{ scaleX: 1 }}
        transition={{
          duration: DURATION.data,
          ease: EASE.spatial,
          delay: staggerFor(index),
        }}
      />
    </span>
  );
}
