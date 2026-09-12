import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import type { JSX } from 'react';

import { dwellFor, type Notice } from './Notice';
import { useDwell } from './useDwell';
import { useNotices } from './useNotices';
import { DURATION, EASE } from '../motion/tokens';
import { IconButton } from '../ui/IconButton';

interface NoticeCentreProps {
  label: string;

  dismissLabel: string;
}

interface StandingNoticeProps {
  notice: Notice;

  dismissLabel: string;

  onDismiss: (id: string) => void;
}

function StandingNotice({ notice, dismissLabel, onDismiss }: StandingNoticeProps): JSX.Element {
  const stillness = useReducedMotion();
  const dwell = dwellFor(notice);

  const { held, hold, release } = useDwell(dwell, () => onDismiss(notice.id));

  return (
    <motion.li
      className="ui-notice"
      data-tone={notice.tone}
      data-held={held ? 'true' : 'false'}
      onMouseEnter={hold}
      onMouseLeave={release}
      onFocusCapture={hold}
      onBlurCapture={release}
      layout={stillness !== true}
      initial={stillness === true ? false : { opacity: 0, y: 12, scale: 0.97 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{
        opacity: 0,
        x: 24,
        transition: { duration: DURATION.reveal, ease: EASE.exit },
      }}
      transition={{ duration: DURATION.expand, ease: EASE.spatial }}
    >
      <span className="ui-notice-body">
        <span className="ui-notice-message">{notice.message}</span>
        {notice.detail !== undefined && <span className="ui-notice-detail">{notice.detail}</span>}
      </span>

      {notice.action}

      <IconButton
        icon="close"
        label={dismissLabel}
        onClick={() => {
          onDismiss(notice.id);
        }}
      />

      {dwell !== undefined && stillness !== true && (
        <span
          className="ui-notice-life"
          aria-hidden="true"
          style={{ animationDuration: `${String(dwell)}ms` }}
        />
      )}
    </motion.li>
  );
}

export function NoticeCentre({ label, dismissLabel }: NoticeCentreProps): JSX.Element {
  const { standing, dismiss } = useNotices();

  return (
    <ul className="ui-notice-centre" role="status" aria-live="polite" aria-label={label}>
      <AnimatePresence initial={false}>
        {standing.map((notice) => (
          <StandingNotice
            key={notice.id}
            notice={notice}
            dismissLabel={dismissLabel}
            onDismiss={dismiss}
          />
        ))}
      </AnimatePresence>
    </ul>
  );
}
