import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import type { WaitingOn } from '../api/discoveryApi';
import { useBlockNode } from '../hooks/useDiscovery';
import { Refusal } from './Refusal';

const WAITING_ON: readonly WaitingOn[] = ['CLIENT', 'SUPPLIER', 'COLLEAGUE', 'APPROVAL'];

interface BlockedQuestionProps {
  nodeId: string;
}

export function BlockedQuestion({ nodeId }: BlockedQuestionProps): JSX.Element {
  const { t } = useTranslation();
  const block = useBlockNode();

  const [answered, setAnswered] = useState<WaitingOn | undefined>(undefined);
  const [refusal, setRefusal] = useState<string | undefined>(undefined);

  function answer(waitingOn: WaitingOn): void {
    setRefusal(undefined);
    block.mutate(
      { nodeId, waitingOn },
      {
        onSuccess: () => setAnswered(waitingOn),
        onError: (failed) => setRefusal(failed instanceof ApiError ? failed.code : 'UNKNOWN'),
      },
    );
  }

  return (
    <div className="fo-disc-questions">
      <span className="fo-disc-questions__ask">{t('discovery.blocked.ask')}</span>

      <div className="fo-disc-answer-row">
        {WAITING_ON.map((option) => (
          <button
            key={option}
            type="button"
            className="fo-disc-answer"
            aria-pressed={answered === option}
            onClick={() => answer(option)}
          >
            {t(`discovery.blocked.answer.${option}`)}
          </button>
        ))}
      </div>

      <Refusal code={refusal} />
    </div>
  );
}
