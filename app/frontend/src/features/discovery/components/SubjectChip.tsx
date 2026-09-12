import { useEffect, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { JobOption } from '../api/discoveryApi';

const FADE_AFTER_MS = 2000;

interface SubjectChipProps {
  jobs: readonly JobOption[];

  selectedJobId: string | undefined;
  onChoose: (jobId: string) => void;
}

export function SubjectChip({ jobs, selectedJobId, onChoose }: SubjectChipProps): JSX.Element {
  const { t } = useTranslation();
  const [faded, setFaded] = useState(false);
  const [correcting, setCorrecting] = useState(false);
  const [showing, setShowing] = useState(selectedJobId);

  if (showing !== selectedJobId) {
    setShowing(selectedJobId);
    setFaded(false);
  }

  useEffect(() => {
    const timer = setTimeout(() => setFaded(true), FADE_AFTER_MS);
    return () => clearTimeout(timer);
  }, [selectedJobId]);

  const selected = jobs.find((job) => job.jobId === selectedJobId);

  return (
    <div className="fo-disc-strip__subject">
      <span>{t('discovery.subject.label')}</span>
      {selected === undefined ? (
        <span className="fo-disc-chip" data-faded={faded}>
          {t('discovery.subject.none')}
        </span>
      ) : (
        <>
          <span className="fo-disc-chip" data-faded={faded} title={t('discovery.subject.guessed')}>
            {selected.name}
          </span>
          {!correcting && jobs.length > 1 ? (
            <button
              type="button"
              className="fo-disc-chip__correct"
              onClick={() => setCorrecting(true)}
            >
              {t('discovery.subject.correct')}
            </button>
          ) : null}
        </>
      )}
      {correcting || (selected === undefined && jobs.length > 0) ? (
        <select
          className="ui-control"
          aria-label={t('discovery.subject.choose')}
          value={selectedJobId ?? ''}
          onChange={(event) => {
            onChoose(event.target.value);
            setCorrecting(false);
          }}
        >
          {selected === undefined ? <option value="">{t('discovery.subject.none')}</option> : null}
          {jobs.map((job) => (
            <option key={job.jobId} value={job.jobId}>
              {job.name}
            </option>
          ))}
        </select>
      ) : null}
    </div>
  );
}
