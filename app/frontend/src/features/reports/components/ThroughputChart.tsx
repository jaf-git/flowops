import { motion } from 'framer-motion';
import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { DURATION, EASE, staggerFor } from '../../../shared/motion/tokens';

import type { SupportedLocale } from '../../../i18n';

export interface ThroughputWeek {
  starting: string;
  created: number;
  closed: number;
}

interface ThroughputChartProps {
  weeks: readonly ThroughputWeek[];
  locale: SupportedLocale;

  span: number;
  onSpan: (weeks: number) => void;
}

const SPANS = [4, 12, 26, 52] as const;

const LABELLED_UP_TO = 12;

export function ThroughputChart({
  weeks,
  locale,
  span,
  onSpan,
}: ThroughputChartProps): JSX.Element {
  const { t } = useTranslation();
  const peak = Math.max(1, ...weeks.map((week) => Math.max(week.created, week.closed)));
  const month = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' });
  const labelled = weeks.length <= LABELLED_UP_TO;

  return (
    <section className="ui-card fo-panel" aria-label={t('reports.throughput.heading')}>
      <header className="ui-chart-head">
        <div>
          <h3 className="fo-panel-title">{t('reports.throughput.heading')}</h3>
          <p className="ui-chart-lead">{t('reports.throughput.lead')}</p>
        </div>

        <div className="ui-chart-spans" role="group" aria-label={t('reports.throughput.span')}>
          {SPANS.map((option) => (
            <button
              key={option}
              type="button"
              className="ui-chart-span"
              aria-pressed={option === span}
              onClick={() => onSpan(option)}
            >
              {t('reports.throughput.weeks', { count: option })}
            </button>
          ))}
        </div>
      </header>

      <ul className="ui-chart-legend">
        <li>
          <span className="ui-chart-swatch ui-chart-swatch--created" aria-hidden="true" />
          {t('reports.throughput.created')}
        </li>
        <li>
          <span className="ui-chart-swatch ui-chart-swatch--closed" aria-hidden="true" />
          {t('reports.throughput.closed')}
        </li>
      </ul>

      <div className={labelled ? 'ui-chart ui-chart--labelled' : 'ui-chart'} aria-hidden="true">
        {weeks.map((week, index) => {
          const label = month.format(new Date(week.starting));
          return (
            <div key={week.starting} className="ui-chart-week">
              <div className="ui-chart-bars">
                <Bar
                  value={week.created}
                  peak={peak}
                  series="created"
                  labelled={labelled}
                  index={index}
                  label={`${label} · ${t('reports.throughput.created')}: ${week.created}`}
                />
                <Bar
                  value={week.closed}
                  peak={peak}
                  series="closed"
                  labelled={labelled}
                  index={index}
                  label={`${label} · ${t('reports.throughput.closed')}: ${week.closed}`}
                />
              </div>

              <span className="ui-chart-tick">
                {index % Math.ceil(weeks.length / 6) === 0 ? label : ' '}
              </span>
            </div>
          );
        })}
      </div>

      <table className="sr-only">
        <caption>{t('reports.throughput.heading')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('reports.throughput.week')}</th>
            <th scope="col">{t('reports.throughput.created')}</th>
            <th scope="col">{t('reports.throughput.closed')}</th>
          </tr>
        </thead>
        <tbody>
          {weeks.map((week) => (
            <tr key={week.starting}>
              <th scope="row">{month.format(new Date(week.starting))}</th>
              <td>{week.created}</td>
              <td>{week.closed}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function Bar({
  value,
  peak,
  series,
  labelled,
  label,
  index,
}: {
  value: number;
  peak: number;
  series: 'created' | 'closed';
  labelled: boolean;
  label: string;
  index: number;
}): JSX.Element | null {
  if (value === 0) {
    return null;
  }
  return (
    <motion.span
      className={`ui-chart-bar ui-chart-bar--${series}`}
      style={{ height: `${(value / peak) * 100}%`, originY: 1 }}
      title={label}
      initial={{ scaleY: 0, opacity: 0 }}
      animate={{ scaleY: 1, opacity: 1 }}
      transition={{
        duration: DURATION.data,
        ease: EASE.spatial,
        delay: staggerFor(index),
      }}
    >
      {labelled ? <span className="ui-chart-value">{value}</span> : null}
    </motion.span>
  );
}
