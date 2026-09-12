import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { AssignmentContext } from '../api/chatApi';

interface AssignWorkControlsProps {
  context: AssignmentContext | undefined;
  onGiveTask: () => void;
  onStartProcess: () => void;
}

export function AssignWorkControls({
  context,
  onGiveTask,
  onStartProcess,
}: AssignWorkControlsProps): JSX.Element | null {
  const { t } = useTranslation();

  if (context === undefined || context.counterpartId === null || !context.counterpartActive) {
    return null;
  }

  return (
    <div style={{ display: 'flex', gap: 'var(--space-2)', marginInlineStart: 'auto' }}>
      {context.mayAssignTask ? (
        <Glyph
          letter={t('chat.assign.taskGlyph')}
          label={t('chat.assign.task')}
          onClick={onGiveTask}
        />
      ) : null}
      {context.mayStartRun ? (
        <Glyph
          letter={t('chat.assign.processGlyph')}
          label={t('chat.assign.process')}
          onClick={onStartProcess}
        />
      ) : null}
    </div>
  );
}

function Glyph({
  letter,
  label,
  onClick,
}: {
  letter: string;
  label: string;
  onClick: () => void;
}): JSX.Element {
  return (
    <button
      type="button"
      className="fo-icon-button"
      onClick={onClick}
      aria-label={label}
      title={label}
      style={{
        border: '1px solid var(--line)',
        width: 28,
        height: 28,
        fontFamily: 'inherit',
        fontSize: 'var(--text-sm)',
        fontWeight: 600,
        color: 'var(--brand-dark)',
      }}
    >
      <span aria-hidden="true">{letter}</span>
    </button>
  );
}
