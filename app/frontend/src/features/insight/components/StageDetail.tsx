import { useMemo, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { STAGES, type Stage } from '../api/analysisApi';
import type { StuckGroup } from '../api/stuckApi';

export function StageDetail({
  stage,
  groups,
  onOpenItem,
  onClose,
}: {
  stage: Stage;
  groups: readonly StuckGroup[];

  onOpenItem?: (itemKind: string, itemId: string) => void;
  onClose: () => void;
}): JSX.Element {
  const { t } = useTranslation();

  const reading = useMemo(() => {
    const here = STAGES.indexOf(stage);

    let arrived = 0;
    let stopped = 0;
    const rows: StuckGroup[] = [];

    for (const group of groups) {
      const at = STAGES.indexOf(group.lastStage);
      if (at < here) {
        continue;
      }

      arrived += group.count;
      if (at === here) {
        stopped += group.count;
        rows.push(group);
      }
    }

    return {
      arrived,
      stopped,
      wentOn: arrived - stopped,
      rows: [...rows].sort((a, b) => b.count - a.count || a.reason.localeCompare(b.reason)),
    };
  }, [groups, stage]);

  return (
    <section className="fo-sd">
      <div className="fo-pb-between fo-pb-between--top">
        <div className="fo-pb-stack fo-pb-stack--tight">
          <span className="fo-pb-eyebrow">{t(`board.stageQuestion.${stage.toLowerCase()}`)}</span>
          <h3 className="fo-pb-title">{t(`board.stage.${stage.toLowerCase()}`)}</h3>
        </div>
        <button
          type="button"
          className="fo-pb-close"
          aria-label={t('board.clearSelection')}
          onClick={onClose}
        >
          ✕
        </button>
      </div>

      <div className="fo-sd-figures">
        <Figure label={t('stage.reachedIt')} n={reading.arrived} />
        <Figure label={t('stage.stoppedHere')} n={reading.stopped} loud={reading.stopped > 0} />
        <Figure label={t('stage.wentOn')} n={reading.wentOn} />
      </div>

      {reading.rows.length === 0 ? (
        <p className="fo-fw-foot">
          {reading.arrived === 0 ? t('stage.nothingReachedIt') : t('stage.nothingStopped')}
        </p>
      ) : (
        <div className="fo-pb-stack fo-pb-stack--tight">
          <span className="fo-pb-eyebrow">{t('stage.whatStopped')}</span>
          {reading.rows.map((row) => (
            <div className="fo-sd-row" key={`${row.itemKind}-${row.reason}`}>
              <span className="fo-sd-kind">{row.itemKind}</span>
              <span className="fo-pb-stack fo-pb-stack--tight fo-pb-grow">
                <span className="fo-sd-reason">{row.reason}</span>
                <span className="fo-fw-foot">{explain(row.reason, t)}</span>
              </span>
              <span className="fo-sd-count">{row.count}</span>
              {onOpenItem === undefined ? null : (
                <button
                  type="button"
                  className="fo-ev-link"
                  onClick={() => {
                    onOpenItem(row.itemKind, row.itemId);
                  }}
                >
                  {t('stage.openOne')} <span aria-hidden="true">↗</span>
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <p className="fo-fw-foot">{t('stage.foot')}</p>
    </section>
  );
}

function Figure({
  label,
  n,
  loud = false,
}: {
  label: string;
  n: number;
  loud?: boolean;
}): JSX.Element {
  return (
    <div className={loud ? 'fo-sd-figure fo-sd-figure--loud' : 'fo-sd-figure'}>
      <span className="fo-pb-eyebrow">{label}</span>
      <span className="fo-sd-figure-n">{n}</span>
    </div>
  );
}

function explain(reason: string, t: (key: string) => string): string {
  const known: Record<string, string> = {
    'gate:job_boundary': 'stage.why.jobBoundary',
    'gate:text_no_signal': 'stage.why.noSignal',
    'gate:no_governed_work_type': 'stage.why.noWorkType',
    no_eligible_template: 'stage.why.noEligibleTemplate',
    'veto:text_floor': 'stage.why.textFloor',
    below_floor: 'stage.why.belowFloor',
    low_confidence: 'stage.why.lowConfidence',
  };

  const key = known[reason];
  return key === undefined ? '' : t(key);
}
