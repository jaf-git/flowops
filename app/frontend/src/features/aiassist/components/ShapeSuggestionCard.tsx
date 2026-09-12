import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Spinner } from '../../../shared/ui/Spinner';
import { useShapeSuggestion } from '../hooks/useShapeSuggestion';

interface ShapeSuggestionCardProps {
  templateId: string;

  wanted: boolean;
}

export function ShapeSuggestionCard({
  templateId,
  wanted,
}: ShapeSuggestionCardProps): JSX.Element | null {
  const { t } = useTranslation();
  const suggestion = useShapeSuggestion(templateId, wanted);

  if (!wanted) {
    return null;
  }

  if (suggestion.isPending) {
    return (
      <section className="fo-shape-ai">
        <Spinner label={t('aiassist.shape.thinking')} />
      </section>
    );
  }

  if (suggestion.isError || suggestion.data === undefined) {
    return null;
  }
  if (!suggestion.data.available || !suggestion.data.suggested) {
    return null;
  }

  const { steps, evidenceLines, partial } = suggestion.data;

  return (
    <section className="fo-shape-ai" aria-labelledby="shape-ai-heading">
      <h2 className="fo-usage-band-title" id="shape-ai-heading">
        {t('aiassist.shape.heading')}
      </h2>

      <p className="fo-shape-ai-lead">{t('aiassist.shape.lead')}</p>

      <ol className="fo-shape-ai-steps">
        {steps.map((step) => (
          <li key={step.sourceKey}>{step.text}</li>
        ))}
      </ol>

      <p className="fo-shape-ai-evidence">
        {t('aiassist.shape.basedOn', { count: evidenceLines })}
        {partial && <> · {t('aiassist.shape.partial')}</>}
      </p>

      <p className="fo-shape-ai-caveat">{t('aiassist.shape.caveat')}</p>
    </section>
  );
}
