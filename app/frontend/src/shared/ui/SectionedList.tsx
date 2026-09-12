import type { JSX, ReactNode } from 'react';

export interface SectionDescriptor {
  key: string;
  label: string;
  count: number;
}

interface SectionedListProps {
  sections: SectionDescriptor[];

  open: readonly string[];
  onToggle: (key: string) => void;

  renderSection: (key: string) => ReactNode;

  emptyLabel: ReactNode;
}

export function SectionedList({
  sections,
  open,
  onToggle,
  renderSection,
  emptyLabel,
}: SectionedListProps): JSX.Element {
  const everythingIsEmpty = sections.every((section) => section.count === 0);

  if (everythingIsEmpty) {
    return <div className="fo-section-empty">{emptyLabel}</div>;
  }

  return (
    <div className="fo-sections">
      {sections.map((section) => {
        const expanded = open.includes(section.key);
        const openable = section.count > 0;
        return (
          <section key={section.key} className="fo-section" data-open={expanded}>
            <h3 className="fo-section-heading">
              <button
                type="button"
                className="fo-section-header"
                aria-expanded={expanded}
                aria-controls={`section-${section.key}`}
                disabled={!openable}
                onClick={() => onToggle(section.key)}
              >
                <span aria-hidden="true" className="fo-section-caret" data-open={expanded}>
                  ▸
                </span>
                <span className="fo-section-label">{section.label}</span>
                <span className="fo-section-count">{section.count}</span>
              </button>
            </h3>

            {expanded && (
              <div id={`section-${section.key}`} className="fo-section-body">
                {renderSection(section.key)}
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}
