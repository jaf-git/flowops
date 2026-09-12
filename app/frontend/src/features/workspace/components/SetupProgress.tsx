import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Icon } from '../../../shared/ui/Icon';
import { isReady, satisfiedCount, stepsOf, type SetupDraft } from '../model/setupDraft';

interface SetupProgressProps {
  draft: SetupDraft;
}

export function SetupProgress({ draft }: SetupProgressProps): JSX.Element {
  const { t } = useTranslation();
  const steps = stepsOf(draft);
  const done = satisfiedCount(draft);
  const ready = isReady(draft);

  return (
    <aside
      aria-label={t('workspace.setup.panel.label')}
      style={{
        position: 'relative',
        overflow: 'hidden',
        background: 'var(--surface)',
        borderLeft: '1px solid var(--line)',
        padding: 'var(--space-8) var(--space-7)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-6)',
        minHeight: '100%',
      }}
    >
      <div className="fo-waiting-field" />

      <div
        style={{
          position: 'relative',
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-2)',
        }}
      >
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 'var(--space-2)',
          }}
          className="fo-eyebrow"
        >
          <Icon name="clock" size={15} />
          {t('workspace.setup.panel.kicker')}
        </span>

        <p
          aria-live="polite"
          style={{ margin: 0, color: 'var(--ink)', fontSize: 'var(--text-lg)', lineHeight: 1.4 }}
        >
          {ready
            ? t('workspace.setup.panel.ready')
            : t('workspace.setup.panel.counted', { done, total: steps.length })}
        </p>
      </div>

      <ol
        style={{
          position: 'relative',
          listStyle: 'none',
          margin: 0,
          padding: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-4)',
        }}
      >
        {steps.map((step) => (
          <li
            key={step.field}
            style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}
          >
            <span className={step.satisfied ? 'fo-mark fo-mark-done' : 'fo-mark'}>
              {step.satisfied && <Icon name="check" size={13} strokeWidth={3} />}
            </span>
            <span
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-1)',
                minWidth: 0,
              }}
            >
              <span
                style={{
                  color: step.satisfied ? 'var(--slate)' : 'var(--muted)',
                  fontSize: 'var(--text-md)',
                  fontWeight: 500,
                  lineHeight: 1.4,
                }}
              >
                {t(`workspace.setup.${step.field}.label`)}
              </span>
              {step.satisfied && (
                <span
                  style={{
                    color: 'var(--ink)',
                    fontSize: 'var(--text-md)',
                    lineHeight: 1.4,
                    overflowWrap: 'anywhere',
                  }}
                >
                  {step.field === 'use' ? t(`workspace.setup.use.${draft.use}`) : step.answer}
                </span>
              )}
            </span>
          </li>
        ))}
      </ol>

      <p
        style={{
          position: 'relative',
          marginTop: 'auto',
          marginBottom: 0,
          color: 'var(--muted)',
          fontSize: 'var(--text-sm)',
          lineHeight: 1.5,
        }}
      >
        {t('workspace.setup.panel.later')}
      </p>
    </aside>
  );
}
