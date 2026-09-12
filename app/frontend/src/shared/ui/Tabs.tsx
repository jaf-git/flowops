import { motion, useReducedMotion } from 'framer-motion';
import { useRef, type JSX } from 'react';

import { DURATION, EASE } from '../motion/tokens';

export interface Tab {
  id: string;
  label: string;
  disabled?: boolean;
}

interface TabsProps {
  label: string;
  tabs: readonly Tab[];
  active: string;
  onSelect: (id: string) => void;
}

export function Tabs({ label, tabs, active, onSelect }: TabsProps): JSX.Element {
  const list = useRef<HTMLDivElement>(null);
  const stillness = useReducedMotion();

  function move(from: number, step: number): void {
    const selectable = tabs.filter((tab) => tab.disabled !== true);

    if (selectable.length === 0) {
      return;
    }

    const current = selectable.findIndex((tab) => tab.id === tabs[from]?.id);
    const next = selectable[(current + step + selectable.length) % selectable.length];

    if (next !== undefined) {
      onSelect(next.id);
      list.current?.querySelector<HTMLElement>(`[data-tab="${next.id}"]`)?.focus();
    }
  }

  function toEdge(edge: 'first' | 'last'): void {
    const selectable = tabs.filter((tab) => tab.disabled !== true);
    const target = edge === 'first' ? selectable[0] : selectable[selectable.length - 1];

    if (target !== undefined) {
      onSelect(target.id);
      list.current?.querySelector<HTMLElement>(`[data-tab="${target.id}"]`)?.focus();
    }
  }

  return (
    <div className="ui-tabs" role="tablist" aria-label={label} ref={list}>
      {tabs.map((tab, index) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          id={`tab-${tab.id}`}
          data-tab={tab.id}
          className="ui-tab"
          aria-selected={tab.id === active}
          aria-controls={`panel-${tab.id}`}
          disabled={tab.disabled}

          tabIndex={tab.id === active ? 0 : -1}
          onClick={() => onSelect(tab.id)}
          onKeyDown={(event) => {
            const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];

            if (step !== undefined) {
              event.preventDefault();
              move(index, step);
            } else if (event.key === 'Home' || event.key === 'End') {
              event.preventDefault();
              toEdge(event.key === 'Home' ? 'first' : 'last');
            }
          }}
        >
          {tab.id === active && stillness !== true && (
            <motion.span
              className="ui-tab-here"
              layoutId="ui-tab-here"
              aria-hidden="true"
              transition={{ duration: DURATION.expand, ease: EASE.spatial }}
            />
          )}

          <span className="ui-tab-label">{tab.label}</span>
        </button>
      ))}
    </div>
  );
}
