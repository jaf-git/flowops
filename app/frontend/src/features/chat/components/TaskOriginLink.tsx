import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { useTaskOrigin } from '../hooks/useChat';

interface TaskOriginLinkProps {
  taskId: string;

  onOpen: (conversationId: string) => void;
}

export function TaskOriginLink({ taskId, onOpen }: TaskOriginLinkProps): JSX.Element | null {
  const { t } = useTranslation();
  const origin = useTaskOrigin(taskId);

  if (!origin.isSuccess || origin.data === undefined) {
    return null;
  }

  const conversationId = origin.data.conversationId;

  return (
    <button
      type="button"
      className="ui-button ui-button-quiet"
      onClick={() => onOpen(conversationId)}
    >
      {t('chat.origin.open')}
    </button>
  );
}
