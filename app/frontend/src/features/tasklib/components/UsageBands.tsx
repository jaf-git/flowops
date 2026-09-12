import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { PhaseBreakdown } from '../../../shared/ui/PhaseBreakdown';
import type { LiveWork, TemplateUsage } from '../api/taskTemplateApi';

const LIVE_LABEL: Record<keyof LiveWork, string> = {
  notStarted: 'task.band.not-started',
  running: 'task.band.in-progress',
  blocked: 'task.state.BLOCKED',
  inReview: 'tasklib.usage.live.inReview',
  finished: 'task.state.CLOSED',
  overdue: 'task.band.overdue',
};

const LIVE_ORDER: readonly (keyof LiveWork)[] = [
  'overdue',
  'blocked',
  'running',
  'inReview',
  'notStarted',
  'finished',
];

function ActiveTime({ seconds }: { seconds: number }): JSX.Element {
  return <PhaseBreakdown spans={[{ phase: 'active', seconds }]} />;
}

const LIVE_PARAM: Record<keyof LiveWork, string> = {
  notStarted: 'not-started',
  running: 'running',
  blocked: 'blocked',
  inReview: 'in-review',
  finished: 'finished',
  overdue: 'overdue',
};

function LiveBand({
  live,
  renderTasks,
}: {
  live: LiveWork;
  renderTasks: UsageBandsProps['renderTasks'];
}): JSX.Element {
  const { t } = useTranslation();
  const [open, setOpen] = useState<keyof LiveWork | undefined>(undefined);

  return (
    <section className="ui-band" aria-labelledby="fo-usage-live">
      <h2 className="ui-band-title" id="fo-usage-live">
        {t('tasklib.usage.live.heading')}
      </h2>

      <ul className="ui-band-counts">
        {LIVE_ORDER.map((field) => {
          const empty = live[field] === 0;
          const label = t(LIVE_LABEL[field]);
          const inside = (
            <>
              <span className="ui-band-count-value">{live[field]}</span>
              <span className="ui-band-count-label">{label}</span>
            </>
          );
          return (
            <li
              className="ui-band-count"
              key={field}
              data-field={field}
              data-empty={empty}
              data-open={open === field}
            >
              {empty ? (
                <span className="ui-band-count-still">{inside}</span>
              ) : (
                <button
                  type="button"
                  className="ui-band-count-open"
                  aria-expanded={open === field}
                  onClick={() => setOpen(open === field ? undefined : field)}
                >
                  {inside}
                </button>
              )}
            </li>
          );
        })}
      </ul>

      {open !== undefined && (
        <div className="ui-band-open">
          <p className="ui-band-open-title">
            {t('tasklib.usage.live.showing', { count: live[open], band: t(LIVE_LABEL[open]) })}
          </p>
          {renderTasks(LIVE_PARAM[open])}
        </div>
      )}
    </section>
  );
}

function DurationBand({ usage }: { usage: TemplateUsage }): JSX.Element {
  const { t } = useTranslation();

  const estimate =
    usage.estimatedHours === null ? (
      <p className="ui-band-none">{t('tasklib.usage.duration.noEstimate')}</p>
    ) : (
      <p className="ui-band-figure">
        {t('tasklib.usage.duration.estimateValue', { hours: usage.estimatedHours })}
      </p>
    );

  const spread =
    usage.lowerQuartileSeconds === null || usage.upperQuartileSeconds === null ? null : (
      <div className="ui-band-spread">
        <ActiveTime seconds={usage.lowerQuartileSeconds} />
        <span className="ui-band-spread-to">{t('tasklib.usage.duration.to')}</span>
        <ActiveTime seconds={usage.upperQuartileSeconds} />
      </div>
    );

  return (
    <section className="ui-band" aria-labelledby="fo-usage-duration">
      <h2 className="ui-band-title" id="fo-usage-duration">
        {t('tasklib.usage.duration.heading')}
      </h2>

      <div className="ui-band-pair">
        <div className="ui-band-half">
          <p className="ui-band-label">{t('tasklib.usage.duration.estimated')}</p>
          {estimate}
        </div>

        <div className="ui-band-half">
          <p className="ui-band-label">{t('tasklib.usage.duration.actual')}</p>

          {usage.measuredTasks === 0 ? (
            <p className="ui-band-none">{t('tasklib.usage.duration.nothingMeasured')}</p>
          ) : usage.varyWidely ? (
            <>
              {spread}
              <p className="ui-band-note">{t('tasklib.usage.duration.varyWidely')}</p>
              {usage.medianActiveSeconds !== null && (
                <div className="ui-band-sub">
                  <span>{t('tasklib.usage.duration.middleValue')}</span>
                  <ActiveTime seconds={usage.medianActiveSeconds} />
                </div>
              )}
            </>
          ) : (
            <>
              {usage.medianActiveSeconds !== null && (
                <div className="ui-band-figure">
                  <ActiveTime seconds={usage.medianActiveSeconds} />
                </div>
              )}
              {spread !== null && !usage.tooFewToAverage && (
                <div className="ui-band-sub">
                  <span>{t('tasklib.usage.duration.middleHalf')}</span>
                  {spread}
                </div>
              )}
            </>
          )}

          {usage.measuredTasks > 0 && usage.tooFewToAverage && (
            <p className="ui-band-thin" data-testid="thin-sample">
              {t('tasklib.usage.duration.tooFew', { count: usage.measuredTasks })}
            </p>
          )}
          {usage.measuredTasks > 0 && !usage.tooFewToAverage && (
            <p className="ui-band-sub">
              {t('tasklib.usage.duration.measuredFrom', { count: usage.measuredTasks })}
            </p>
          )}
        </div>
      </div>
    </section>
  );
}

function ApprovalBand({ usage }: { usage: TemplateUsage }): JSX.Element {
  const { t } = useTranslation();

  return (
    <section className="ui-band" aria-labelledby="fo-usage-approval">
      <h2 className="ui-band-title" id="fo-usage-approval">
        {t('tasklib.usage.approval.heading')}
      </h2>

      {usage.reviewed === 0 ? (
        <p className="ui-band-none">{t('tasklib.usage.approval.noneReviewed')}</p>
      ) : (
        <p className="ui-band-figure" data-testid="first-try">
          {t('tasklib.usage.approval.counts', {
            passed: usage.passedFirstTime,
            reviewed: usage.reviewed,
          })}
        </p>
      )}
      <p className="ui-band-note">{t('tasklib.usage.approval.notAboutPeople')}</p>
    </section>
  );
}

export function UsageBands({ usage, renderTasks }: UsageBandsProps): JSX.Element {
  const { t } = useTranslation();

  if (usage.stamped === 0) {
    return (
      <section className="ui-band ui-band-unused" data-testid="never-used">
        <h2 className="ui-band-title">{t('tasklib.usage.neverUsed.heading')}</h2>
        <p className="ui-band-none">{t('tasklib.usage.neverUsed.body')}</p>
      </section>
    );
  }

  return (
    <>
      <LiveBand live={usage.live} renderTasks={renderTasks} />
      <DurationBand usage={usage} />
      <ApprovalBand usage={usage} />
    </>
  );
}

export interface UsageBandsProps {
  usage: TemplateUsage;

  renderTasks: (band: string) => JSX.Element;
}
