import type { JSX, ReactNode } from 'react';

interface WorkbenchPageProps {
  readonly list: ReactNode;

  readonly focus: ReactNode;

  readonly inspector?: ReactNode;
  readonly listLabel: string;
}

export function WorkbenchPage({
  list,
  focus,
  inspector,
  listLabel,
}: WorkbenchPageProps): JSX.Element {
  return (
    <div className="fo-workbench">
      <aside className="fo-workbench-list" aria-label={listLabel}>
        {list}
      </aside>

      <section className="fo-workbench-focus">{focus}</section>

      {inspector}
    </div>
  );
}
