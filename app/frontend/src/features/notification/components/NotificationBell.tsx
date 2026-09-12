import { useEffect, useRef, useState, type JSX, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Icon } from '../../../shared/ui/Icon';
import { useUnreadCount } from '../hooks/useNotifications';
import type { NotificationSubject } from '../model/notification';
import { NotificationInbox } from './NotificationInbox';

interface NotificationBellProps {
  onOpenSubject: (subject: NotificationSubject) => void;
}

export function NotificationBell({ onOpenSubject }: NotificationBellProps): JSX.Element {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const bell = useRef<HTMLButtonElement>(null);
  const count = useUnreadCount();

  const unread = count.data?.unread ?? 0;

  useEffect(() => {
    if (!open) {
      return;
    }
    function onEscape(event: globalThis.KeyboardEvent): void {
      if (event.key === 'Escape') {
        setOpen(false);
        bell.current?.focus();
      }
    }
    document.addEventListener('keydown', onEscape);
    return () => document.removeEventListener('keydown', onEscape);
  }, [open]);

  function openSubject(subject: NotificationSubject): void {
    setOpen(false);
    onOpenSubject(subject);
  }

  return (
    <div
      style={{ position: 'relative', display: 'inline-flex' }}
      onKeyDown={(event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape') {
          event.stopPropagation();
        }
      }}
    >
      <button
        ref={bell}
        type="button"
        className="ui-nav-item"
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => setOpen(!open)}
        style={{ width: 'auto', flexShrink: 0, gap: 'var(--space-2)' }}
      >
        <Icon name="bell" size={17} strokeWidth={1.6} />
        <span className="sr-only">{t('notification.bell.label')}</span>

        {unread === 0 ? null : (
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--muted)' }}>{unread}</span>
        )}
      </button>

      {open ? (
        <div
          role="dialog"
          aria-label={t('notification.inbox.title')}
          style={{
            position: 'absolute',
            insetInlineEnd: 0,
            insetBlockStart: 'calc(100% + var(--space-2))',
            zIndex: 40,

            width: 'min(24rem, calc(100vw - 2 * var(--space-5)))',
            maxWidth: 'calc(100vw - 2 * var(--space-5))',
            maxHeight: 'min(32rem, 70vh)',
            overflowY: 'auto',
            overflowX: 'hidden',
            background: 'var(--surface)',
            border: '1px solid var(--line)',
            borderRadius: 'var(--radius-card)',
            boxShadow: 'var(--shadow-lg)',
            textAlign: 'start',
          }}
        >
          <NotificationInbox onOpenSubject={openSubject} />
        </div>
      ) : null}
    </div>
  );
}
