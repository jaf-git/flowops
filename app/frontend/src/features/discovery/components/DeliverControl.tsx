import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { Closable } from '../api/workApi';
import { useDeliverWork, useDeliverableHere } from '../hooks/useWorkCircles';

interface DeliverControlProps {
  readonly conversationId: string;
  readonly messageId: string;
}

export function DeliverControl({ conversationId, messageId }: DeliverControlProps): JSX.Element {
  const { t } = useTranslation();
  const [asked, setAsked] = useState(false);

  const here = useDeliverableHere(messageId, asked);
  const deliver = useDeliverWork(conversationId);

  const offer = here.data;

  const nothingToClose = offer !== undefined && offer.mine.length === 0;

  function send(work: Closable): void {
    deliver.mutate({ messageId, bracketId: work.bracketId, jobId: work.jobId });
  }

  if (deliver.isSuccess) {
    const done = deliver.data;

    return (
      <span className="fo-mark-strip" data-marked="true">
        <span aria-hidden="true">●</span>
        {t('discovery.circles.delivered', { where: done.destination })}
        {done.unblocked.length === 0 ? null : (
          <span className="fo-mark-count">
            {t('discovery.circles.unblocked', { who: done.unblocked.join(', ') })}
          </span>
        )}
      </span>
    );
  }

  return (
    <>
      <button
        type="button"
        className="fo-mark-deliver"
        aria-label={t('discovery.circles.deliverLabel')}
        title={t('discovery.circles.deliverLabel')}
        data-dimmed={nothingToClose}
        aria-expanded={asked}
        onClick={() => {
          setAsked((open) => !open);
        }}
      >
        <span aria-hidden="true" />
      </button>

      {!asked ? null : (
        <div className="fo-message-flow">
          <div className="fo-message-flow__head">
            <span className="fo-message-flow__case">{t('discovery.circles.caseDeliver')}</span>
            <button
              type="button"
              className="fo-message-flow__close"
              aria-label={t('discovery.strip.close')}
              onClick={() => {
                setAsked(false);
              }}
            >
              <span aria-hidden="true">✕</span>
            </button>
          </div>

          {here.isPending ? (
            <p className="fo-mark-help">{t('discovery.circles.reading')}</p>
          ) : here.isError || offer === undefined ? (
            <p className="fo-mark-failed">{t('discovery.circles.readFailed')}</p>
          ) : offer.mine.length === 0 ? (
            <p className="fo-mark-help">
              {offer.heldByOthers.length === 0
                ? t('discovery.circles.nothingOfYours')
                : t('discovery.circles.heldByOthers', { who: offer.heldByOthers.join(', ') })}
            </p>
          ) : offer.mine.length === 1 ? (
            <Confirmation
              work={offer.mine[0] as Closable}
              busy={deliver.isPending}
              onDeliver={send}
            />
          ) : (
            <>
              <p className="fo-mark-help">{t('discovery.circles.whichOne')}</p>
              <div className="fo-mark-familiar">
                {offer.mine.map((work) => (
                  <Confirmation
                    key={work.bracketId}
                    work={work}
                    busy={deliver.isPending}
                    onDeliver={send}
                  />
                ))}
              </div>
            </>
          )}

          {deliver.isError ? (
            <p className="fo-mark-failed">{t('discovery.circles.deliverFailed')}</p>
          ) : null}
        </div>
      )}
    </>
  );
}

function Confirmation({
  work,
  busy,
  onDeliver,
}: {
  readonly work: Closable;
  readonly busy: boolean;
  readonly onDeliver: (work: Closable) => void;
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <div className="fo-mark-familiar">
      <p className="fo-mark-help">
        {t('discovery.circles.thisCloses', { where: work.destination })}
      </p>
      {work.waitingHolderNames.length === 0 ? null : (
        <p className="fo-mark-help">
          {t('discovery.circles.unblocks', { who: work.waitingHolderNames.join(', ') })}
        </p>
      )}
      <div className="fo-mark-nearest">
        <button
          type="button"
          className="fo-mark-do"
          disabled={busy}
          onClick={() => {
            onDeliver(work);
          }}
        >
          {busy ? t('discovery.circles.delivering') : t('discovery.circles.deliverConfirm')}
        </button>
      </div>
    </div>
  );
}
