import { useCallback, useEffect, useRef, useState } from 'react';

export interface Dwell {
  readonly held: boolean;

  readonly hold: () => void;

  readonly release: () => void;
}

export function useDwell(dwell: number | undefined, onExpire: () => void): Dwell {
  const [held, setHeld] = useState(false);
  const remaining = useRef(dwell);
  const startedAt = useRef(0);
  const expire = useRef(onExpire);

  useEffect(() => {
    expire.current = onExpire;
  });

  useEffect(() => {
    if (dwell === undefined || held) {
      return;
    }

    const left = remaining.current ?? dwell;
    startedAt.current = Date.now();

    const timer = setTimeout(() => expire.current(), left);
    return () => clearTimeout(timer);
  }, [dwell, held]);

  const hold = useCallback(() => {
    if (dwell === undefined) {
      return;
    }

    const spent = Date.now() - startedAt.current;
    remaining.current = Math.max(0, (remaining.current ?? dwell) - spent);
    setHeld(true);
  }, [dwell]);

  const release = useCallback(() => {
    setHeld(false);
  }, []);

  return { held, hold, release };
}
