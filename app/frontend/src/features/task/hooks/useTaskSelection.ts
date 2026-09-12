import { useCallback, useMemo, useState } from 'react';

export const FEWEST_TASKS_IN_A_PROCESS = 2;

export interface TaskSelection {
  readonly chosen: readonly string[];
  readonly count: number;
  readonly enough: boolean;
  isChosen: (taskId: string) => boolean;
  toggle: (taskId: string) => void;
  clear: () => void;
}

export function useTaskSelection(): TaskSelection {
  const [chosen, setChosen] = useState<readonly string[]>([]);

  const toggle = useCallback((taskId: string) => {
    setChosen((current) =>
      current.includes(taskId) ? current.filter((each) => each !== taskId) : [...current, taskId],
    );
  }, []);

  const clear = useCallback(() => {
    setChosen([]);
  }, []);

  const membership = useMemo(() => new Set(chosen), [chosen]);
  const isChosen = useCallback((taskId: string) => membership.has(taskId), [membership]);

  return {
    chosen,
    count: chosen.length,
    enough: chosen.length >= FEWEST_TASKS_IN_A_PROCESS,
    isChosen,
    toggle,
    clear,
  };
}
