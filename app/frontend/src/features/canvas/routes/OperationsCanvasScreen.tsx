import { useMemo, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ProcessGraphCanvas } from '../components/ProcessGraphCanvas';
import { instanceFixture, INSTANCE_NOW } from '../model/instance';

export function OperationsCanvasScreen(): JSX.Element {
  const { t } = useTranslation();

  const [announcement, setAnnouncement] = useState<string | undefined>(undefined);
  const sayItIsInert = (): void => setAnnouncement(t('canvas.gallery.inert'));
  const [selected, setSelected] = useState<string | undefined>(undefined);

  const { steps, edges } = useMemo(() => instanceFixture(t), [t]);

  return (
    <main style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <header
        style={{
          padding: 'var(--space-5) var(--space-6)',
          borderBottom: '1px solid var(--line)',
          background: 'var(--surface)',
        }}
      >
        <h1 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 600 }}>
          {t('canvas.instance.title')}
        </h1>
        <p
          style={{
            margin: 'var(--space-1) 0 0',
            color: 'var(--muted)',
            fontSize: 'var(--text-sm)',
            maxWidth: '80ch',
          }}
        >
          {t('canvas.instance.lead')}
        </p>
        <p
          role="status"
          data-testid="canvas-inert-notice"
          style={{
            margin: 'var(--space-1) 0 0',
            minHeight: '1.5em',
            color: 'var(--muted)',
            fontSize: 'var(--text-sm)',
          }}
        >
          {announcement}
        </p>
      </header>

      <div style={{ flex: 1, minHeight: 0 }} data-testid="operations-plane">
        <ProcessGraphCanvas
          steps={steps}
          edges={edges}
          now={INSTANCE_NOW}
          selected={selected}
          onSelect={setSelected}
          onAssign={sayItIsInert}
        />
      </div>
    </main>
  );
}
