import { motion, useReducedMotion } from 'framer-motion';
import { useEffect, useId, useRef, type JSX, type ReactNode } from 'react';

import { DURATION, EASE, RISE_PX } from '../motion/tokens';

interface DialogProps {
  open: boolean;

  onCancel: () => void;
  title: string;

  children: ReactNode;

  actions: ReactNode;

  initialFocus?: 'cancel' | 'content';
}

export function Dialog({
  open,
  onCancel,
  title,
  children,
  actions,
  initialFocus = 'cancel',
}: DialogProps): JSX.Element | null {
  const dialog = useRef<HTMLDialogElement>(null);
  const body = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const stillness = useReducedMotion();

  useEffect(() => {
    const element = dialog.current;

    if (element === null) {
      return;
    }

    if (!open) {
      if (element.open) {
        element.close();
      }
      return;
    }

    if (!element.open) {
      if (typeof element.showModal === 'function') {
        element.showModal();
      } else {
        element.setAttribute('open', '');
      }
    }

    const target =
      initialFocus === 'cancel'
        ? element.querySelector<HTMLElement>('[data-dialog-cancel]')
        : (body.current?.querySelector<HTMLElement>(
            'input, select, textarea, [tabindex]:not([tabindex="-1"])',
          ) ?? null);

    target?.focus();
  }, [open, initialFocus]);

  return (
    <dialog
      ref={dialog}
      className="ui-dialog"
      aria-labelledby={titleId}

      onCancel={(event) => {
        event.preventDefault();
        onCancel();
      }}

      onClick={(event) => {
        if (event.target !== dialog.current) {
          return;
        }
        const box = dialog.current.getBoundingClientRect();
        const inside =
          event.clientX >= box.left &&
          event.clientX <= box.right &&
          event.clientY >= box.top &&
          event.clientY <= box.bottom;
        if (!inside) {
          onCancel();
        }
      }}
    >
      <motion.div
        className="ui-dialog-body"
        ref={body}
        initial={stillness === true ? false : { opacity: 0, scale: 0.96, y: RISE_PX }}
        animate={open ? { opacity: 1, scale: 1, y: 0 } : {}}
        transition={{ duration: DURATION.expand, ease: EASE.spatial }}
      >
        <h2 id={titleId} className="ui-dialog-title">
          {title}
        </h2>
        {children}
        <div className="ui-dialog-actions">{actions}</div>
      </motion.div>
    </dialog>
  );
}
