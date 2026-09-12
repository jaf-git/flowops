import { animate, useReducedMotion } from 'framer-motion';
import { useEffect, useRef, useState, type JSX } from 'react';

import { DURATION, EASE } from './tokens';

interface CountUpProps {
  value: number;

  format?: (value: number) => string;
}

function shownAs(value: number, format: ((value: number) => string) | undefined): string {
  return format === undefined ? String(Math.round(value)) : format(value);
}

function Rolling({ value, format }: CountUpProps): JSX.Element {
  const [frame, setFrame] = useState(value);
  const from = useRef(value);

  useEffect(() => {
    const controls = animate(from.current, value, {
      duration: DURATION.data,
      ease: EASE.standard,
      onUpdate: (step) => setFrame(Math.round(step * 100) / 100),
    });

    from.current = value;
    return () => controls.stop();
  }, [value]);

  return <>{shownAs(frame, format)}</>;
}

export function CountUp({ value, format }: CountUpProps): JSX.Element {
  const stillness = useReducedMotion();

  if (stillness === true) {
    return <>{shownAs(value, format)}</>;
  }

  return <Rolling value={value} format={format} />;
}
