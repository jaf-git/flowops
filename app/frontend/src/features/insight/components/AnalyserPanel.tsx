import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { AnalyserLine, AnalyserRun } from '../api/analyserApi';
import { WhatWouldUnblock } from './WhatWouldUnblock';

interface AnalyserPanelProps {
  run: AnalyserRun | null;
  onRun: () => void;
  running: boolean;
}

export function AnalyserPanel({ run, onRun, running }: AnalyserPanelProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <section className="fo-analysers" aria-labelledby="fo-analysers-title">
      <div className="fo-analysers__head">
        <div>
          <h3 className="fo-analysers__title" id="fo-analysers-title">
            {t('pipeline.analysers.title')}
          </h3>
          <p className="fo-analysers__why">{t('pipeline.analysers.why')}</p>
        </div>
        <button
          type="button"
          className="ui-button ui-button-secondary"
          onClick={onRun}
          disabled={running}
        >
          {running ? t('pipeline.analysers.running') : t('pipeline.analysers.run')}
        </button>
      </div>

      {run === null ? (
        <p className="fo-analysers__never">{t('pipeline.analysers.never')}</p>
      ) : (
        <>
          <p className="fo-analysers__window">
            {t('pipeline.analysers.window', {
              from: new Date(run.windowFrom).toLocaleDateString(),
              to: new Date(run.windowTo).toLocaleDateString(),
            })}
          </p>

          <WhatWouldUnblock run={run} />

          <ul className="fo-analysers__list">
            {run.analysers.map((analyser) => (
              <AnalyserRow key={analyser.id} analyser={analyser} />
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

function AnalyserRow({ analyser }: { analyser: AnalyserLine }): JSX.Element {
  const { t } = useTranslation();

  const unmet = analyser.preconditions.filter((precondition) => !precondition.met);
  const blocked = analyser.failure !== null || unmet.length > 0;

  return (
    <li className="fo-analyser" data-blocked={blocked}>
      <div className="fo-analyser__head">
        <span className="fo-analyser__id">{analyser.id}</span>
        <span className="fo-analyser__counts">
          {t('pipeline.analysers.read', { count: analyser.itemsRead })} ·{' '}
          {t('pipeline.analysers.findings', { count: analyser.findings })}
        </span>
        {blocked ? (
          <span className="fo-analyser__blocked">{t('pipeline.analysers.blocked')}</span>
        ) : null}
      </div>

      {analyser.clean.length > 0 ? (
        <ul className="fo-analyser__notes fo-analyser__clean">
          {analyser.clean.map((clean) => (
            <li key={clean.what}>{clean.detail}</li>
          ))}
        </ul>
      ) : null}

      {analyser.findings === 0 && analyser.clean.length === 0 && !blocked ? (
        <p className="fo-analyser__silence">
          {analyser.itemsRead > 0
            ? t('pipeline.analysers.silentButLooked', { count: analyser.itemsRead })
            : t('pipeline.analysers.silentAndBlind')}
        </p>
      ) : null}

      {analyser.failure !== null ? (
        <p className="fo-analyser__failure">
          {t('pipeline.analysers.failed', { failure: analyser.failure })}
        </p>
      ) : null}

      {unmet.length > 0 ? (
        <div className="fo-analyser__group">
          <span className="fo-analyser__group-label">{t('pipeline.analysers.needed')}</span>
          <ul className="fo-analyser__notes">
            {unmet.map((precondition) => (
              <li key={precondition.needed}>
                {t('pipeline.analysers.preconditionUnmet', {
                  needed: precondition.needed,
                  had: precondition.had,
                })}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {analyser.absences.length > 0 ? (
        <div className="fo-analyser__group">
          <span className="fo-analyser__group-label">{t('pipeline.analysers.couldNotSee')}</span>
          <ul className="fo-analyser__notes">
            {analyser.absences.map((absence) => (
              <li key={absence.what} data-blocking={absence.blocking}>
                {absence.detail}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </li>
  );
}
