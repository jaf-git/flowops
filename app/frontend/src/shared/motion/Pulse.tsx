import { motion, useReducedMotion } from 'framer-motion';
import type { JSX } from 'react';

import { EASE } from './tokens';

interface PulseProps {
  live: boolean;

  label: string;
}

export function Pulse({ live, label }: PulseProps): JSX.Element {
  const stillness = useReducedMotion();
  const beating = live && stillness !== true;

  return (
    <motion.span
      className="ui-pulse"
      role="img"
      aria-label={label}
      animate={beating ? { opacity: [1, 0.35, 1], scale: [1, 0.82, 1] } : { opacity: 1, scale: 1 }}
      transition={
        beating ? { duration: 1.8, ease: EASE.standard, repeat: Infinity } : { duration: 0 }
      }
    />
  );
}
