import type { JSX, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

interface PublicPageFrameProps {
  heading?: string;
  children: ReactNode;
}

export function PublicPageFrame({ heading, children }: PublicPageFrameProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <main className="ui-public">
      <div className="ui-public-column">
        <h1 className="ui-public-wordmark">{t('app.name')}</h1>

        {heading !== undefined && <h2 className="ui-public-heading">{heading}</h2>}

        {children}
      </div>
    </main>
  );
}
