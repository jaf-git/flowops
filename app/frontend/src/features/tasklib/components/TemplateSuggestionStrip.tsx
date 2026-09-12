import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import type { TemplateSuggestion } from '../api/taskTemplateApi';

interface TemplateSuggestionStripProps {
  suggestions: readonly TemplateSuggestion[];

  locale: string;
  onUse: (suggestion: TemplateSuggestion) => void;
  busy: boolean;
}

export function TemplateSuggestionStrip({
  suggestions,
  locale,
  onUse,
  busy,
}: TemplateSuggestionStripProps): JSX.Element | null {
  const { t } = useTranslation();

  if (suggestions.length === 0) {
    return null;
  }

  return (
    <aside className="fo-suggestion-strip" aria-live="polite">
      <p className="fo-suggestion-lead">{t('tasklib.suggest.lead')}</p>
      <ul className="fo-suggestion-list">
        {suggestions.map((suggestion) => (
          <li key={suggestion.id}>
            <span className="fo-suggestion-name">{suggestion.title}</span>
            <span className="fo-suggestion-used">
              {t('tasklib.card.used', { count: suggestion.timesUsed })}
            </span>
            <Button variant="secondary" onClick={() => onUse(suggestion)} disabled={busy}>
              {t('tasklib.suggest.use')}
            </Button>
            <a
              className="fo-suggestion-open"
              href={`/${locale}/templates?focus=${suggestion.id}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('tasklib.suggest.open')}
            </a>
          </li>
        ))}
      </ul>
    </aside>
  );
}
