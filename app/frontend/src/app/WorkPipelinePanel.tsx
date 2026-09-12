import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { useWorkPipeline } from './useWorkPipeline';
import { stageSentence } from './workStageSentence';

interface WorkPipelinePanelProps {
  taskId: string;
  taskTitle: string;
  taskState: string;

  templateId: string | null;
}

export function WorkPipelinePanel({
  taskId,
  taskTitle,
  taskState,
  templateId,
}: WorkPipelinePanelProps): JSX.Element | null {
  const { t } = useTranslation();
  const { stages } = useWorkPipeline({ taskId, taskTitle, taskState, templateId });

  return (
    <section aria-label={t('pipeline.heading')} className="fo-chain">
      <div className="fo-chain-head">
        <h3 className="fo-chain-title">{t('pipeline.heading')}</h3>
        <span className="fo-chain-birth">
          {t(`pipeline.birth.${String(stages[0]?.facts.birth ?? 'TYPED')}`, { defaultValue: '' })}
        </span>
      </div>

      <ol className="fo-chain-list">
        {stages.map((stage, index) => (
          <li key={`${stage.kind}-${stage.subjectId}`} className="fo-chain-item">
            <span aria-hidden="true" className="fo-chain-rail">
              <span className="fo-chain-dot" />
              {index < stages.length - 1 ? <span className="fo-chain-line" /> : null}
            </span>

            <span className="fo-chain-body">
              <span className="fo-eyebrow">{t(`pipeline.stage.${stage.kind}`)}</span>
              <span className="fo-chain-sentence">{stageSentence(stage, t)}</span>
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
