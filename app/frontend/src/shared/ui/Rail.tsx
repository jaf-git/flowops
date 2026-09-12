import { motion, useReducedMotion } from 'framer-motion';
import type { JSX, ReactNode } from 'react';

import { DURATION, EASE } from '../motion/tokens';

export interface RailItem {
  readonly id: string;

  readonly glyph: string;

  readonly name: string;
}

interface RailProps {
  readonly items: readonly RailItem[];

  readonly footer: readonly RailItem[];
  readonly active: string | undefined;
  readonly onSelect: (id: string) => void;

  readonly brand: ReactNode;
  readonly label: string;
}

export function Rail({ items, footer, active, onSelect, brand, label }: RailProps): JSX.Element {
  const stillness = useReducedMotion();

  const mark = (item: RailItem): JSX.Element => {
    const here = active === item.id;

    return (
      <button
        key={item.id}
        type="button"
        className="fo-rail-mark"
        aria-current={here ? 'page' : undefined}
        aria-label={item.name}
        title={item.name}
        onClick={() => {
          onSelect(item.id);
        }}
      >
        {here && stillness !== true && (
          <motion.span
            className="fo-rail-here"
            layoutId="fo-rail-here"
            aria-hidden="true"
            transition={{ duration: DURATION.transition, ease: EASE.spatial }}
          />
        )}

        <span className="fo-rail-glyph" aria-hidden="true">
          {item.glyph}
        </span>
      </button>
    );
  };

  return (
    <nav className="fo-rail" aria-label={label}>
      <div className="fo-rail-brand">{brand}</div>

      <div className="fo-rail-stack">{items.map(mark)}</div>

      <div className="fo-rail-foot">{footer.map(mark)}</div>
    </nav>
  );
}
