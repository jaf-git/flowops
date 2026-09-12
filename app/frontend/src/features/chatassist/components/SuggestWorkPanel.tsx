import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { AssignablePerson } from '../../../shared/model/people';
import { useCreateAgreedWork } from '../hooks/useCreateAgreedWork';
import { useWorkSuggestion } from '../hooks/useWorkSuggestion';
import { ProposeWorkDialog } from './ProposeWorkDialog';

interface SuggestWorkPanelProps {
  conversationId: string;

  people: readonly AssignablePerson[];
}

export function SuggestWorkPanel({
  conversationId,
  people,
}: SuggestWorkPanelProps): JSX.Element | null {
  const { t } = useTranslation();
  const asking = useWorkSuggestion(conversationId);
  const creating = useCreateAgreedWork();
  const [dismissed, setDismissed] = useState(false);
  const [reviewing, setReviewing] = useState(false);

  const answer = asking.data;

  if (dismissed) {
    return null;
  }

  // An answer of "nothing here" is an answer, and the person asked for it. Unmounting the
  // panel on `available: false` used to take the button away with it, so pressing it looked
  // like pressing a dead control -- and `nothingHere` below could never be reached.
  const foundNothing = answer !== undefined && (!answer.available || answer.steps.length === 0);

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-2)',
        padding: 'var(--space-3)',
        borderTop: '1px solid var(--faint)',
      }}
    >
      <div
        style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}
      >
        <button
          type="button"
          className="ui-button ui-button-quiet"
          disabled={asking.isPending}
          onClick={() => {
            asking.mutate();
          }}
        >
          {asking.isPending ? t('chatAssist.asking') : t('chatAssist.ask')}
        </button>

        {asking.isError ? (
          <span role="alert" style={{ color: 'var(--alert)', fontSize: 'var(--text-sm)' }}>
            {t('chatAssist.failed')}
          </span>
        ) : null}
      </div>

      {foundNothing && !asking.isPending ? (
        <p style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
          {t('chatAssist.nothingHere')}
        </p>
      ) : null}

      {answer !== undefined && answer.available && answer.steps.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          <p style={{ margin: 0, fontSize: 'var(--text-sm)', lineHeight: 1.45 }}>
            {t('chatAssist.draftIntro', { count: answer.steps.length })}
          </p>

          <ol
            style={{
              margin: 0,
              paddingLeft: 'var(--space-4)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-1)',
            }}
          >
            {answer.steps.map((step, at) => (
              <li key={`${String(at)}-${step.title.value}`} style={{ fontSize: 'var(--text-sm)' }}>
                {step.title.value}
              </li>
            ))}
          </ol>

          <p
            style={{
              margin: 0,
              color: 'var(--muted)',
              fontSize: 'var(--text-sm)',
              lineHeight: 1.45,
            }}
          >
            {t('chatAssist.checkIt')}
          </p>

          <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="ui-button ui-button-primary"
              onClick={() => setReviewing(true)}
            >
              {t('chatAssist.useDraft')}
            </button>
            <button
              type="button"
              className="ui-button ui-button-quiet"
              onClick={() => setDismissed(true)}
            >
              {t('chatAssist.notNow')}
            </button>
          </div>
        </div>
      ) : null}

      {reviewing && answer !== undefined && answer.shape !== null ? (
        <ProposeWorkDialog
          draft={answer}
          people={people}
          busy={creating.isPending}
          refusal={creating.error === null ? null : creating.error.message}
          onClose={() => setReviewing(false)}
          onSubmit={(agreed) =>
            creating.mutate(agreed, {
              onSuccess: () => {
                setReviewing(false);
                setDismissed(true);
              },
            })
          }
        />
      ) : null}
    </div>
  );
}
