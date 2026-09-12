import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { useEnrichNode } from '../hooks/useDiscovery';
import { Refusal } from './Refusal';

interface DescribeWorkProps {
  nodeId: string;

  title?: string | null;

  onDone?: () => void;
}

export function DescribeWork({ nodeId, title, onDone }: DescribeWorkProps): JSX.Element {
  const { t } = useTranslation();
  const describe = useEnrichNode();

  const [draftTitle, setDraftTitle] = useState('');
  const [draftDetail, setDraftDetail] = useState('');

  const [draftSteps, setDraftSteps] = useState('');
  const [named, setNamed] = useState<string | null>(title ?? null);

  const [correctable, setCorrectable] = useState(false);
  const [correcting, setCorrecting] = useState(false);
  const [alreadyAnswered, setAlreadyAnswered] = useState(false);
  const [refusal, setRefusal] = useState<string | undefined>(undefined);

  const steps = draftSteps
    .split('\n')
    .map((step) => step.trim())
    .filter((step) => step !== '');

  const nothingTyped = draftTitle.trim() === '' && draftDetail.trim() === '' && steps.length === 0;

  function save(): void {
    setRefusal(undefined);
    setAlreadyAnswered(false);
    describe.mutate(
      {
        nodeId,

        fields: {
          ...(draftTitle.trim() === '' ? {} : { title: draftTitle.trim() }),
          ...(draftDetail.trim() === '' ? {} : { detail: draftDetail.trim() }),

          ...(steps.length === 0 ? {} : { checklist: steps }),
        },
      },
      {
        onSuccess: (saved) => {
          setNamed(saved.title);
          setCorrectable(saved.correctable);
          setAlreadyAnswered(!saved.accepted);
          setCorrecting(false);
          setDraftTitle('');
          setDraftDetail('');
          setDraftSteps('');
          if (saved.accepted) {
            onDone?.();
          }
        },
        onError: (failed) => {
          setRefusal(failed instanceof ApiError ? failed.code : 'UNKNOWN');
        },
      },
    );
  }

  return (
    <div className="fo-disc-describe">
      <span className="fo-disc-questions__ask">{t('discovery.describe.ask')}</span>

      <p className="fo-disc-describe__standing">
        {named === null || named === ''
          ? t('discovery.describe.unnamed')
          : t('discovery.describe.named', { title: named })}
      </p>

      {named !== null && named !== '' && correctable && !correcting ? (
        <button
          type="button"
          className="fo-disc-answer"
          onClick={() => {
            setDraftTitle(named);
            setCorrecting(true);
          }}
        >
          {t('discovery.describe.correct')}
        </button>
      ) : null}

      {named === null || named === '' || correcting ? (
        <>
          <label className="fo-disc-describe__field">
            <span className="fo-disc-describe__label">{t('discovery.describe.titleLabel')}</span>
            <input
              type="text"
              className="fo-disc-describe__input"

              maxLength={120}
              value={draftTitle}
              placeholder={t('discovery.describe.titlePlaceholder')}
              onChange={(event) => setDraftTitle(event.target.value)}
            />
          </label>

          <label className="fo-disc-describe__field">
            <span className="fo-disc-describe__label">{t('discovery.describe.detailLabel')}</span>
            <textarea
              className="fo-disc-describe__input"
              rows={2}
              value={draftDetail}
              placeholder={t('discovery.describe.detailPlaceholder')}
              onChange={(event) => setDraftDetail(event.target.value)}
            />
          </label>

          <label className="fo-disc-describe__field">
            <span className="fo-disc-describe__label">{t('discovery.describe.stepsLabel')}</span>
            <textarea
              className="fo-disc-describe__input"
              rows={4}
              value={draftSteps}
              placeholder={t('discovery.describe.stepsPlaceholder')}
              onChange={(event) => setDraftSteps(event.target.value)}
            />
          </label>

          <p className="fo-disc-describe__note">
            {correcting ? t('discovery.describe.correctingNote') : t('discovery.describe.onceOnly')}
          </p>

          <button
            type="button"
            className="fo-disc-answer"
            disabled={nothingTyped || describe.isPending}
            onClick={save}
          >
            {describe.isPending ? t('discovery.describe.saving') : t('discovery.describe.save')}
          </button>
        </>
      ) : null}

      {alreadyAnswered ? (
        <Banner tone="info">{t('discovery.describe.alreadyAnswered')}</Banner>
      ) : null}

      <Refusal code={refusal} />
    </div>
  );
}
