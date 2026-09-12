import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { TaskNodeCard } from '../components/TaskNodeCard';
import { galleryEntries, GALLERY_NOW } from '../model/gallery';

function useInertAssignment(): { announcement: string | undefined; sayItIsInert: () => void } {
  const { t } = useTranslation();
  const [announcement, setAnnouncement] = useState<string | undefined>(undefined);

  return { announcement, sayItIsInert: () => setAnnouncement(t('canvas.gallery.inert')) };
}

function InertNotice({ announcement }: { announcement: string | undefined }): JSX.Element {
  return (
    <p
      role="status"
      data-testid="canvas-inert-notice"
      style={{
        margin: 0,
        minHeight: '1.5em',
        color: 'var(--muted)',
        fontSize: 'var(--text-sm)',
        lineHeight: 1.5,
      }}
    >
      {announcement}
    </p>
  );
}

export function NodeGalleryScreen(): JSX.Element {
  const { t } = useTranslation();
  const { announcement, sayItIsInert } = useInertAssignment();

  return (
    <main
      className="canvas-plane"
      style={{ minHeight: '100vh', padding: 'var(--space-7) var(--space-6)' }}
    >
      <header style={{ maxWidth: '62ch', marginBottom: 'var(--space-7)' }}>
        <p className="fo-eyebrow">{t('canvas.gallery.reference')}</p>
        <h1 style={{ margin: 'var(--space-2) 0', fontSize: 'var(--text-2xl)', fontWeight: 600 }}>
          {t('canvas.gallery.title')}
        </h1>
        <p style={{ margin: 0, color: 'var(--muted)', lineHeight: 1.6 }}>
          {t('canvas.gallery.lead')}
        </p>
        <InertNotice announcement={announcement} />
      </header>

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'flex-start',
          gap: 'var(--space-7) var(--space-6)',
        }}
      >
        {galleryEntries(t).map((entry) => (
          <figure
            key={entry.node.id}
            data-testid={`gallery-${entry.node.id}`}
            style={{ margin: 0, width: '228px' }}
          >
            <TaskNodeCard node={entry.node} now={GALLERY_NOW} onAssign={sayItIsInert} />
            <figcaption
              style={{
                marginTop: 'var(--space-3)',
                fontSize: 'var(--text-xs)',
                lineHeight: 1.5,
                color: 'var(--faint)',
              }}
            >
              {entry.note}
            </figcaption>
          </figure>
        ))}
      </div>
    </main>
  );
}
