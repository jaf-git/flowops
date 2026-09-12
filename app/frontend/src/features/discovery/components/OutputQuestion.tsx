import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import type { OutputType } from '../api/discoveryApi';
import { useRecordOutput } from '../hooks/useDiscovery';
import { Refusal } from './Refusal';

const OUTPUTS: readonly OutputType[] = ['TEXT', 'DESIGN', 'REPORT', 'SCHEDULING', 'NONE'];

interface OutputQuestionProps {
  nodeId: string;
}

export function OutputQuestion({ nodeId }: OutputQuestionProps): JSX.Element {
  const { t } = useTranslation();
  const record = useRecordOutput();

  const [answered, setAnswered] = useState<OutputType | undefined>(undefined);
  const [refusal, setRefusal] = useState<string | undefined>(undefined);

  function answer(outputType: OutputType): void {
    setRefusal(undefined);
    record.mutate(
      { nodeId, outputType },
      {
        onSuccess: () => setAnswered(outputType),
        onError: (failed) => setRefusal(failed instanceof ApiError ? failed.code : 'UNKNOWN'),
      },
    );
  }

  return (
    <div className="fo-disc-questions">
      <span className="fo-disc-questions__ask">{t('discovery.output.ask')}</span>

      <div className="fo-disc-answer-row">
        {OUTPUTS.map((option) => (
          <button
            key={option}
            type="button"
            className={option === 'NONE' ? 'fo-disc-answer fo-disc-answer--none' : 'fo-disc-answer'}
            aria-pressed={answered === option}
            onClick={() => answer(option)}
          >
            {t(`discovery.output.answer.${option}`)}
          </button>
        ))}
      </div>

      {answered === 'NONE' ? <Banner tone="info">{t('discovery.output.noneNote')}</Banner> : null}

      <Refusal code={refusal} />
    </div>
  );
}
