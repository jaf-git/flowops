import type { CSSProperties, JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { usePreferences, useSavePreferences } from '../hooks/useNotifications';
import {
  PREFERENCE_SWITCHES,
  type NotificationPreferences as Preferences,
} from '../model/notification';

const ROW: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 'var(--space-3)',
  minHeight: '44px',
};

function trackStyle(on: boolean): CSSProperties {
  return {
    width: '38px',
    height: '22px',
    flexShrink: 0,
    padding: '2px',
    border: '1px solid var(--line)',
    borderRadius: '999px',
    background: on ? 'var(--brand)' : 'var(--surface-sunk)',
    display: 'flex',
    justifyContent: on ? 'flex-end' : 'flex-start',
    alignItems: 'center',
    cursor: 'pointer',
  };
}

const KNOB: CSSProperties = {
  width: '16px',
  height: '16px',
  borderRadius: '50%',
  background: 'var(--surface)',
  border: '1px solid var(--line)',
};

export function NotificationPreferences(): JSX.Element {
  const { t } = useTranslation();
  const preferences = usePreferences(true);
  const save = useSavePreferences();

  const current = preferences.data;

  function toggle(field: keyof Preferences): void {
    if (current === undefined) {
      return;
    }
    save.mutate({ ...current, [field]: !current[field] });
  }

  return (
    <section
      style={{
        borderTop: '1px solid var(--line)',
        paddingTop: 'var(--space-4)',
        marginTop: 'var(--space-4)',
        display: 'grid',
        gap: 'var(--space-2)',
      }}
    >
      <h3 className="fo-eyebrow" style={{ margin: 0 }}>
        {t('notification.prefs.title')}
      </h3>

      {PREFERENCE_SWITCHES.map((field) => {
        const on = current?.[field] ?? false;
        return (
          <div key={field} style={ROW}>
            <span id={`notification-prefs-${field}`} style={{ fontSize: 'var(--text-sm)' }}>
              {t(`notification.prefs.${field}`)}
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={on}
              aria-labelledby={`notification-prefs-${field}`}

              disabled={current === undefined}
              onClick={() => toggle(field)}
              style={trackStyle(on)}
            >
              <span aria-hidden="true" style={KNOB} />
            </button>
          </div>
        );
      })}

      <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--muted)' }}>
        {t('notification.prefs.escalation.note')}
      </p>
    </section>
  );
}
