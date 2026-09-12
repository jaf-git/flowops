import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { StuckGroup } from '../api/stuckApi';
import { stuckReasonInWords } from '../api/stuckWording';
import { FormaliseNodeDialog } from './FormaliseNodeDialog';

const WORTH_WRITING_DOWN = new Set(['no_eligible_template', 'below_floor', 'low_confidence']);

function canBeWrittenDown(group: StuckGroup): boolean {
  return group.itemKind === 'NODE' && WORTH_WRITING_DOWN.has(group.reason);
}

export function StuckPanel({ groups }: { groups: StuckGroup[] }): JSX.Element {
  const { t } = useTranslation();

  const [formalising, setFormalising] = useState<string | undefined>(undefined);

  if (groups.length === 0) {
    return (
      <section className="fo-stuck" aria-labelledby="fo-stuck-title">
        <h2 className="fo-stuck-title" id="fo-stuck-title">
          {t('pipeline.stuck.title')}
        </h2>
        <p className="fo-stuck-empty">{t('pipeline.stuck.none')}</p>
      </section>
    );
  }

  const dropped = groups.reduce((total, group) => total + group.count, 0);

  return (
    <section className="fo-stuck" aria-labelledby="fo-stuck-title">
      <h2 className="fo-stuck-title" id="fo-stuck-title">
        {t('pipeline.stuck.title')}
      </h2>

      <p className="fo-stuck-eyebrow">{t('pipeline.stuck.summary', { count: dropped })}</p>

      <ul className="fo-stuck-list">
        {groups.map((group) => (
          <li className="fo-stuck-row" key={`${group.lastStage}-${group.reason}-${group.itemKind}`}>
            <span className="fo-stuck-stage">{group.lastStage}</span>
            <span className="fo-stuck-reason">{stuckReasonInWords(group.reason)}</span>

            <span className="fo-stuck-count">{group.count}</span>

            {canBeWrittenDown(group) ? (
              <button
                type="button"
                className="ui-button ui-button-quiet fo-stuck-action"
                onClick={() => {
                  setFormalising(group.itemId);
                }}
              >
                {t('pipeline.formalise.open')}
              </button>
            ) : null}
          </li>
        ))}
      </ul>

      {formalising === undefined ? null : (
        <FormaliseNodeDialog
          nodeId={formalising}
          open
          onClose={() => {
            setFormalising(undefined);
          }}
        />
      )}
    </section>
  );
}
