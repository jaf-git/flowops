import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import type { NudgeAnswer } from '../api/discoveryApi';
import { useAnswerNudge, useNudge } from '../hooks/useDiscovery';
import { OutputQuestion } from './OutputQuestion';
import { Refusal } from './Refusal';

const ANSWERS: readonly NudgeAnswer[] = ['DONE', 'STILL_GOING', 'DROPPED', 'WAS_A_QUESTION'];

interface NudgeCardProps {
  enabled?: boolean;
}

export function NudgeCard({ enabled = true }: NudgeCardProps): JSX.Element | null {
  const { t } = useTranslation();
  const nudge = useNudge(enabled);
  const answering = useAnswerNudge();

  const [refusal, setRefusal] = useState<string | undefined>(undefined);
  const [given, setGiven] = useState<NudgeAnswer | undefined>(undefined);
  const [asksForAnOutput, setAsksForAnOutput] = useState(false);
  const [setAside, setSetAside] = useState(false);

  const subject = nudge.data ?? undefined;

  const [shownFor, setShownFor] = useState(subject?.nodeId);
  if (shownFor !== subject?.nodeId) {
    setShownFor(subject?.nodeId);
    setRefusal(undefined);
    setGiven(undefined);
    setAsksForAnOutput(false);
    setSetAside(false);
  }

  if (subject === undefined || setAside) {
    return null;
  }

  function answer(nodeId: string, chosen: NudgeAnswer): void {
    setRefusal(undefined);
    answering.mutate(
      { nodeId, answer: chosen },
      {
        onSuccess: (progress) => {
          setGiven(chosen);
          setAsksForAnOutput(progress.asksForAnOutput);
        },

        onError: (failed) => setRefusal(failed instanceof ApiError ? failed.code : 'UNKNOWN'),
      },
    );
  }

  return (
    <div className="fo-disc-nudge">
      <span className="fo-disc-nudge__ask">{t('discovery.nudge.ask', { name: subject.text })}</span>

      <div className="fo-disc-answer-row">
        {ANSWERS.map((option) => (
          <button
            key={option}
            type="button"
            className="fo-disc-answer"
            aria-pressed={given === option}
            onClick={() => answer(subject.nodeId, option)}
          >
            {t(`discovery.nudge.answer.${option}`)}
          </button>
        ))}

        <button
          type="button"
          className="fo-disc-answer fo-disc-answer--none"
          onClick={() => setSetAside(true)}
        >
          {t('discovery.nudge.dismiss')}
        </button>
      </div>

      {asksForAnOutput ? <OutputQuestion nodeId={subject.nodeId} /> : null}

      <Refusal code={refusal} />
    </div>
  );
}
