import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { AnalyserRun } from '../api/analyserApi';

export function WhatWouldUnblock({
  run,
}: {
  readonly run: AnalyserRun | null;
}): JSX.Element | null {
  const { t } = useTranslation();

  if (run === null) {
    return null;
  }

  const blocked = blockersByAnalyser(run);
  if (blocked.size === 0) {
    return null;
  }

  const weight = new Map<string, number>();
  for (const blockers of blocked.values()) {
    for (const blocker of blockers) {
      weight.set(blocker, (weight.get(blocker) ?? 0) + 1);
    }
  }

  const chosen = [...weight.entries()]
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .slice(0, 3)
    .map(([blocker]) => blocker);

  const freed = [...blocked.values()].filter(
    (blockers) => blockers.length > 0 && blockers.every((blocker) => chosen.includes(blocker)),
  ).length;

  return (
    <p className="fo-analysers__unblock" aria-live="polite">
      <span className="fo-analysers__unblock-do">{joinWithAnd(chosen, t)}</span>{' '}
      {t('pipeline.analysers.unblockResult', { count: freed, of: run.analysers.length })}
    </p>
  );
}

function blockersByAnalyser(run: AnalyserRun): Map<string, string[]> {
  const blocked = new Map<string, string[]>();

  for (const analyser of run.analysers) {
    const blockers = [
      ...analyser.preconditions
        .filter((precondition) => !precondition.met)
        .map((precondition) => precondition.remedy ?? precondition.needed),
      ...analyser.absences.filter((absence) => absence.blocking).map((a) => a.what),
    ]
      .map((blocker) => blocker.trim())
      .filter((blocker) => blocker !== '');

    if (blockers.length > 0) {
      blocked.set(analyser.id, [...new Set(blockers)]);
    }
  }

  return blocked;
}

function joinWithAnd(
  parts: readonly string[],
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  if (parts.length <= 1) {
    return parts[0] ?? '';
  }
  const last = parts[parts.length - 1] ?? '';
  return t('pipeline.analysers.unblockJoin', {
    first: parts.slice(0, -1).join(', '),
    last,
  });
}
