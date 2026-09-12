import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import type { JSX } from 'react';

import { DURATION, EASE, staggerFor } from '../motion/tokens';

export type StageState = 'waiting' | 'running' | 'done' | 'stopped';

export interface RailStage {
  id: string;

  name: string;

  state: StageState;

  outcome?: string;
}

interface StageRailProps {
  stages: readonly RailStage[];

  label: string;
}

function reached(stages: readonly RailStage[]): number {
  return stages.filter((stage) => stage.state === 'done').length;
}

export function StageRail({ stages, label }: StageRailProps): JSX.Element {
  const stillness = useReducedMotion();
  const running = stages.some((stage) => stage.state === 'running');
  const stopped = stages.some((stage) => stage.state === 'stopped');
  const settled = reached(stages);

  return (
    <div className="ui-rail" data-stopped={stopped ? 'true' : 'false'} aria-label={label}>
      <ol className="ui-rail-stages">
        {stages.map((stage, index) => (
          <motion.li
            key={stage.id}
            className="ui-rail-stage"
            data-state={stage.state}
            initial={false}
            animate={{ opacity: stage.state === 'waiting' ? 0.45 : 1 }}
            transition={{ duration: DURATION.reveal, ease: EASE.standard }}
          >
            <span className="ui-rail-name">{stage.name}</span>

            <AnimatePresence>
              {stage.outcome !== undefined && stage.state !== 'waiting' && (
                <motion.span
                  className="ui-rail-outcome"
                  initial={{ opacity: 0, y: -4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  transition={{
                    duration: DURATION.reveal,
                    ease: EASE.spatial,
                    delay: stillness === true ? 0 : staggerFor(index),
                  }}
                >
                  {stage.outcome}
                </motion.span>
              )}
            </AnimatePresence>
          </motion.li>
        ))}
      </ol>

      <div className="ui-rail-track" aria-hidden="true">
        <motion.div
          className="ui-rail-fill"
          initial={false}
          animate={{ scaleX: stages.length === 0 ? 0 : settled / stages.length }}
          transition={{ duration: DURATION.data, ease: EASE.spatial }}
        />

        {running && stillness !== true && (
          <motion.div
            className="ui-rail-sweep"
            initial={{ x: '-40%' }}
            animate={{ x: '140%' }}
            transition={{ duration: 1.1, ease: EASE.standard, repeat: Infinity }}
          />
        )}
      </div>
    </div>
  );
}
