import type { CSSProperties, JSX, ReactNode } from 'react';

interface CardProps {
  eyebrow?: ReactNode;

  title?: ReactNode;

  titleLevel?: 2 | 3 | 4;

  onOpen?: () => void;

  openHref?: string;

  openLabel?: string;

  payload?: ReactNode;

  detail?: ReactNode;
  detailIsProse?: boolean;

  meta?: ReactNode;

  action?: ReactNode;

  children?: ReactNode;

  material?: 'flat' | 'lifted';

  pad?: number | string;

  className?: string;
  style?: CSSProperties;
}

export function Card({
  eyebrow,
  title,
  titleLevel = 3,
  onOpen,
  openHref,
  openLabel = 'Open',
  payload,
  detail,
  detailIsProse = false,
  meta,
  action,
  children,
  material = 'flat',
  pad = 'var(--gap-5)',
  className,
  style,
}: CardProps): JSX.Element {
  const Title = `h${titleLevel}` as const;

  return (
    <div
      className={[
        'ui-card',
        'ui-card-slots',
        material === 'lifted' ? 'ui-card--lifted' : null,
        className,
      ]
        .filter((name) => name !== undefined && name !== null)
        .join(' ')}
      style={{ padding: pad, ...style }}
    >
      {eyebrow === undefined ? null : <div className="ui-card-eyebrow">{eyebrow}</div>}

      {title === undefined ? null : (
        <div className="ui-card-head">
          <Title className="ui-card-title">{title}</Title>

          {openHref !== undefined ? (
            <a className="ui-card-open" href={openHref} aria-label={openLabel} title={openLabel}>
              <span aria-hidden="true">↗</span>
            </a>
          ) : onOpen === undefined ? null : (
            <button
              type="button"
              className="ui-card-open"
              onClick={onOpen}
              aria-label={openLabel}
              title={openLabel}
            >
              <span aria-hidden="true">↗</span>
            </button>
          )}
        </div>
      )}

      {payload === undefined ? null : <div className="ui-card-payload">{payload}</div>}
      {children}

      {detail === undefined ? null : (
        <div className={detailIsProse ? 'ui-card-detail ui-card-detail--prose' : 'ui-card-detail'}>
          {detail}
        </div>
      )}

      {meta === undefined ? null : <div className="ui-card-meta">{meta}</div>}
      {action === undefined ? null : <div className="ui-card-action">{action}</div>}
    </div>
  );
}
