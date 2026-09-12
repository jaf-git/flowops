import { useEffect, useRef, type JSX, type ReactNode } from 'react';

import { IconButton } from './IconButton';

interface ToastRegionProps {
  label: string;
  children: ReactNode;
}

export function ToastRegion({ label, children }: ToastRegionProps): JSX.Element {
  return (
    <div className="ui-toast-region" role="status" aria-live="polite" aria-label={label}>
      {children}
    </div>
  );
}

interface ToastProps {
  children: ReactNode;

  action?: ReactNode;

  onDismiss?: () => void;
  dismissLabel?: string;

  autoDismissAfterMs?: number;
}

export function Toast({
  children,
  action,
  onDismiss,
  dismissLabel,
  autoDismissAfterMs,
}: ToastProps): JSX.Element {
  const dismiss = useRef(onDismiss);

  useEffect(() => {
    dismiss.current = onDismiss;
  });

  useEffect(() => {
    if (autoDismissAfterMs === undefined) {
      return;
    }

    const timer = setTimeout(() => dismiss.current?.(), autoDismissAfterMs);
    return () => clearTimeout(timer);
  }, [autoDismissAfterMs]);

  return (
    <div className="ui-toast">
      <span className="ui-toast-message">{children}</span>
      {action}
      {onDismiss !== undefined && dismissLabel !== undefined && (
        <IconButton icon="close" label={dismissLabel} onClick={onDismiss} />
      )}
    </div>
  );
}
