import type { JSX, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import type { SupportedLocale } from '../../../i18n';
import { Avatar } from '../../../shared/ui/Avatar';
import {
  cardTone,
  healthOf,
  readyForSomebody,
  stepPosition,
  unsequencedIn,
  type Band,
} from '../model/bands';
import { HEALTH, TONE_COLOUR } from '../model/tone';

function OverviewStrip({ band }: { band: Band }): JSX.Element | null {
  const { t } = useTranslation();

  if (band.cards.length === 0) {
    return null;
  }

  return (
    <div
      role="img"
      aria-label={t('canvas.board.overviewOf', { name: band.name })}
      style={{ display: 'flex', gap: '2px', marginBlockEnd: 'var(--space-2)' }}
    >
      {band.cards.map((card) => (
        <span
          key={card.id}
          style={{
            flex: 1,
            height: '5px',
            borderRadius: '2px',
            minWidth: '3px',
            background: TONE_COLOUR[cardTone(card)],
          }}
        />
      ))}
    </div>
  );
}

export interface RunHeadProps {
  band: Band;
  locale: SupportedLocale;

  ownerName: string | undefined;
  onAddTask: (instanceId: string) => void;

  action?: ReactNode;
}

export function RunHead({ band, locale, ownerName, onAddTask, action }: RunHeadProps): JSX.Element {
  const { t } = useTranslation();
  const health = healthOf(band.cards);
  const tone = HEALTH[health];
  const step = stepPosition(band);
  const ready = readyForSomebody(band.cards);
  const loose = unsequencedIn(band.cards);
  const percent =
    band.progress.total === 0 ? 0 : (band.progress.closed / band.progress.total) * 100;

  return (
    <div style={{ position: 'relative', width: '100%' }}>
      <OverviewStrip band={band} />

      <Link
        to={`/${locale}/canvas/process/${band.id}`}
        className="nodrag"

        draggable={false}
        style={{
          font: '600 var(--text-md) var(--font-sans)',
          color: 'var(--ink)',
          textDecoration: 'none',
          letterSpacing: '-0.01em',
          display: 'block',
          marginBlockEnd: 'var(--space-2)',
          lineHeight: 1.25,
        }}
      >
        {band.name}
      </Link>

      <div
        style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}
      >
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '5px',
            fontSize: 'var(--text-xs)',
            fontWeight: 500,
            color: tone.ink,
            background: tone.soft,
            border: `1px solid ${tone.edge}`,
            borderRadius: 'var(--radius-chip)',
            padding: '2px var(--space-2)',
          }}
        >
          <span
            aria-hidden="true"
            style={{ width: '5px', height: '5px', borderRadius: '50%', background: tone.ink }}
          />
          {t(`canvas.board.health.${health}`)}
        </span>

        {band.paused ? (
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--faint)' }}>
            {t('canvas.board.paused')}
          </span>
        ) : null}
        {!band.running && !band.paused ? (
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--done)' }}>
            {t('canvas.board.complete')}
          </span>
        ) : null}
      </div>

      <p
        style={{
          margin: 'var(--space-2) 0 var(--space-1)',
          fontSize: 'var(--text-xs)',
          color: 'var(--faint)',
        }}
      >
        {step === null
          ? t('canvas.board.progress', { closed: band.progress.closed, total: band.progress.total })
          : t('canvas.board.stepOf', { at: step.at, of: step.of })}
      </p>
      <div
        style={{
          height: '3px',
          width: '100%',
          background: 'var(--line-soft)',
          borderRadius: '2px',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            height: '100%',
            width: `${percent}%`,
            background: 'var(--brand)',
            borderRadius: '2px',
          }}
        />
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-2)',
          margin: 'var(--space-2) 0',
          minWidth: 0,
        }}
      >
        <Avatar id={band.ownerId} name={ownerName ?? ''} size={16} />
        <span
          style={{
            fontSize: 'var(--text-xs)',
            color: 'var(--faint)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {ownerName ?? t('canvas.board.formerMember')}
        </span>
      </div>

      {ready === null || !band.running ? null : (
        <p
          style={{
            margin: '0 0 var(--space-2)',
            background: 'var(--brand-soft)',
            border: '1px solid var(--brand-line)',
            borderRadius: 'var(--radius-sm)',
            padding: '6px var(--space-3)',
            color: 'var(--brand-dark)',
            fontSize: 'var(--text-xs)',
            fontWeight: 500,
            lineHeight: 1.35,
          }}
        >
          {t('canvas.board.needsSomebody', { step: ready.title })}
        </p>
      )}

      {loose.length === 0 ? null : (
        <p
          style={{
            margin: '0 0 var(--space-2)',
            background: 'color-mix(in srgb, var(--waiting) 8%, var(--surface))',
            border: '1px solid color-mix(in srgb, var(--waiting) 30%, var(--line))',
            borderRadius: 'var(--radius-sm)',
            padding: '5px var(--space-3)',
            color: 'var(--waiting)',
            fontSize: 'var(--text-xs)',
            fontWeight: 500,
            lineHeight: 1.35,
          }}
        >
          {t('canvas.board.unsequenced', { count: loose.length })}
        </p>
      )}

      <div
        className="nodrag"
        style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}
      >
        {action === undefined ? null : action}
        <button
          type="button"
          className="fo-add-task-card"
          style={{ width: '100%', minHeight: '28px', fontSize: 'var(--text-xs)' }}
          onClick={() => onAddTask(band.id)}
          disabled={!band.running}
        >
          {t('canvas.board.addTask')}
        </button>
      </div>
    </div>
  );
}
