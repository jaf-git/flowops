import type { JSX, ReactNode } from 'react';

export interface FilterPreset {
  key: string;
  label: string;

  value?: string;
  active: boolean;
  onPick: () => void;
}

interface FilterBarProps {
  query: string;
  onQuery: (query: string) => void;

  children?: ReactNode;

  presets?: FilterPreset[];
  onClear?: () => void;
  labels: {
    search: string;
    searchPlaceholder: string;
    presets: string;
    clear: string;
  };
}

export function FilterBar({
  query,
  onQuery,
  children,
  presets = [],
  onClear,
  labels,
}: FilterBarProps): JSX.Element {
  return (
    <div className="fo-filterbar">
      {presets.length > 0 && (
        <div className="fo-filter-presets" role="group" aria-label={labels.presets}>
          {presets.map((preset) => (
            <button
              key={preset.key}
              type="button"
              className="fo-filter-preset"
              aria-pressed={preset.active}
              onClick={preset.onPick}
            >
              {preset.label}

              {preset.value !== undefined && (
                <span className="fo-filter-preset-value">· {preset.value}</span>
              )}
            </button>
          ))}
        </div>
      )}

      <div className="fo-filter-controls">
        <label className="fo-filter-search">
          <span className="fo-visually-hidden">{labels.search}</span>
          <input
            type="search"
            className="ui-control"
            value={query}
            placeholder={labels.searchPlaceholder}
            onChange={(event) => onQuery(event.target.value)}
          />
        </label>
        {children}
        {onClear !== undefined && (
          <button type="button" className="ui-button ui-button-quiet" onClick={onClear}>
            {labels.clear}
          </button>
        )}
      </div>
    </div>
  );
}
