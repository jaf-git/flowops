import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import type { AssignmentContext } from '../api/chatApi';
import { useStartRunInConversation } from '../hooks/useChat';

interface StartRunDialogProps {
  conversationId: string;
  context: AssignmentContext;
  onClose: () => void;
  onStarted: (instanceId: string) => void;
}

export function StartRunDialog({
  conversationId,
  context,
  onClose,
  onStarted,
}: StartRunDialogProps): JSX.Element {
  const { t } = useTranslation();
  const start = useStartRunInConversation(conversationId);
  const [chosen, setChosen] = useState('');
  const [refusal, setRefusal] = useState<string | null>(null);

  const counterpart = context.counterpartName ?? '';

  function submit(): void {
    setRefusal(null);
    start.mutate(chosen, {
      onSuccess: (created) => {
        onStarted(created.instanceId);
        onClose();
      },

      onError: (failure) =>
        setRefusal(failure instanceof ApiError ? failure.message : t('chat.thread.sendFailed')),
    });
  }

  return (
    <Dialog
      open
      onCancel={onClose}
      title={t('chat.assign.process')}
      actions={
        <>
          <Button variant="quiet" onClick={onClose}>
            {t('chat.assign.cancel')}
          </Button>
          {context.templates.length === 0 ? null : (
            <Button onClick={submit} disabled={chosen === '' || start.isPending}>
              {t('chat.assign.start')}
            </Button>
          )}
        </>
      }
    >
      {refusal !== null ? <Banner tone="alert">{refusal}</Banner> : null}

      {context.templates.length === 0 ? (
        <>
          <p style={{ color: 'var(--muted)', marginBottom: 'var(--space-1)' }}>
            {t('chat.assign.noTemplates')}
          </p>
          <p style={{ color: 'var(--faint)', fontSize: 'var(--text-sm)', margin: 0 }}>
            {t('chat.assign.authorOne')}
          </p>
        </>
      ) : (
        <>
          <fieldset style={{ border: 'none', margin: 0, padding: 0 }}>
            <legend style={{ fontSize: 'var(--text-sm)', color: 'var(--muted)' }}>
              {t('chat.assign.chooseTemplate')}
            </legend>
            {context.templates.map((template) => (
              <label
                key={template.id}
                style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  gap: 'var(--space-2)',
                  padding: 'var(--space-2) 0',
                  cursor: 'pointer',
                }}
              >
                <input
                  type="radio"
                  name="chat-run-template"
                  value={template.id}
                  checked={chosen === template.id}
                  onChange={() => setChosen(template.id)}
                />
                <span style={{ flex: 1 }}>
                  <span style={{ fontWeight: 500 }}>{template.name}</span>
                  {template.overview !== null && template.overview !== '' ? (
                    <span
                      style={{
                        display: 'block',
                        fontSize: 'var(--text-xs)',
                        color: 'var(--faint)',
                      }}
                    >
                      {template.overview}
                    </span>
                  ) : null}
                </span>

                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--faint)' }}>
                  {t('chat.assign.stepCount', { count: template.stepCount })}
                </span>
              </label>
            ))}
          </fieldset>

          <p style={{ marginTop: 'var(--space-3)', color: 'var(--ink)' }}>
            {t('chat.assign.steeredBy', { name: counterpart })}
            <span
              style={{
                display: 'block',
                fontSize: 'var(--text-xs)',
                color: 'var(--faint)',
              }}
            >
              {t('chat.assign.fromThisConversation')}
            </span>
          </p>
        </>
      )}
    </Dialog>
  );
}
