import type { ReactNode } from 'react';
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type JSX,
  type CSSProperties,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import type { SupportedLocale } from '../../../i18n';
import { durationLabel, relativeLabel } from '../../../shared/lib/elapsed';
import { Avatar } from '../../../shared/ui/Avatar';
import { Icon } from '../../../shared/ui/Icon';
import type { Band, BandCard, CardState } from '../model/bands';
import { anchorTip, type CardTip } from '../model/tip';
import { GlyphTile, TileGlyph } from './GlyphTile';
import { StepCardTip } from './StepCardTip';

const STATES: readonly CardState[] = ['notStarted', 'inProgress', 'blocked', 'done'];

const STATE_DOT: Record<CardState, string> = {
  notStarted: 'var(--faint)',
  inProgress: 'var(--brand)',
  blocked: 'var(--alert)',
  done: 'var(--done)',
};

const CARD_TILE_SIZE = 26;

const CARD_FILL: Record<CardState, string> = {
  notStarted: 'var(--glass-surface)',
  inProgress: 'var(--glass-surface)',
  blocked: 'var(--glass-surface)',
  done: 'var(--glass-surface)',
};

const CARD_TILE: Record<CardState, { fill: string; icon: ReactNode }> = {
  notStarted: {
    fill: 'var(--line-soft)',
    icon: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </>
    ),
  },
  inProgress: {
    fill: 'var(--brand-soft)',
    icon: (
      <>
        <rect x="3" y="4" width="7" height="6" rx="1.5" />
        <rect x="14" y="14" width="7" height="6" rx="1.5" />
        <path d="M6.5 10v4a3 3 0 0 0 3 3H14" />
      </>
    ),
  },
  blocked: {
    fill: 'var(--alert-soft)',
    icon: (
      <>
        <path d="M10 5v14" />
        <path d="M14 5v14" />
      </>
    ),
  },
  done: { fill: 'var(--done-soft)', icon: <path d="M20 6 9 17l-5-5" /> },
};

interface OperationsBoardProps {
  sections: readonly BoardSection[];
  loading: boolean;
  locale: SupportedLocale;

  ownerNames: ReadonlyMap<string, string>;
  onAddTask: (instanceId: string) => void;

  onOpenTask: (taskId: string) => void;

  bandAction?: (instanceId: string) => ReactNode;
}

export interface BoardSection {
  key: string;
  title: string;

  named: boolean;
  bands: readonly Band[];
}

export function OperationsBoard({
  sections,
  loading,
  locale,
  ownerNames,
  onAddTask,
  onOpenTask,
  bandAction,
}: OperationsBoardProps): JSX.Element {
  const { t } = useTranslation();
  const [visible, setVisible] = useState<ReadonlySet<CardState>>(new Set(STATES));
  const [zoom, setZoom] = useState(1);

  const SLACK = 400;

  const content = useRef<HTMLDivElement | null>(null);
  const [natural, setNatural] = useState({ width: 0, height: 0 });

  useEffect(() => {
    const element = content.current;
    if (element === null) {
      return undefined;
    }

    const watch = new ResizeObserver(() => {
      setNatural({ width: element.offsetWidth, height: element.offsetHeight });
    });
    watch.observe(element);
    return () => watch.disconnect();
  }, []);
  const viewport = useRef<HTMLDivElement | null>(null);

  const [plane, setPlane] = useState<HTMLDivElement | null>(null);
  const attachPlane = useCallback((node: HTMLDivElement | null) => {
    viewport.current = node;
    setPlane(node);
  }, []);

  const drag = useRef<{
    x: number;
    y: number;
    left: number;
    top: number;
    panning: boolean;
  } | null>(null);

  const suppressClick = useRef(false);

  const anchor = useRef<{ left: number; top: number } | null>(null);

  const [tip, setTip] = useState<CardTip | null>(null);

  function showTip(card: BandCard, element: HTMLElement): void {
    setTip(anchorTip(card, element.getBoundingClientRect()));
  }

  const DRAG_THRESHOLD = 4;

  function beginDrag(event: ReactPointerEvent<HTMLDivElement>): void {
    const view = viewport.current;
    if (view === null || event.button !== 0) {
      return;
    }
    if ((event.target as HTMLElement).closest('input, textarea, select') !== null) {
      return;
    }
    drag.current = {
      x: event.clientX,
      y: event.clientY,
      left: view.scrollLeft,
      top: view.scrollTop,
      panning: false,
    };
  }

  function moveDrag(event: ReactPointerEvent<HTMLDivElement>): void {
    const view = viewport.current;
    const from = drag.current;
    if (view === null || from === null) {
      return;
    }
    const dx = event.clientX - from.x;
    const dy = event.clientY - from.y;

    if (!from.panning) {
      if (Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) {
        return;
      }

      from.panning = true;
      view.setPointerCapture(event.pointerId);
      view.style.cursor = 'grabbing';
    }

    view.scrollLeft = from.left - dx;
    view.scrollTop = from.top - dy;
  }

  function endDrag(event: ReactPointerEvent<HTMLDivElement>): void {
    const view = viewport.current;
    const from = drag.current;
    if (view === null || from === null) {
      return;
    }
    drag.current = null;
    if (!from.panning) {
      return;
    }

    suppressClick.current = true;
    view.releasePointerCapture(event.pointerId);
    view.style.cursor = '';
  }

  function swallowClickAfterPan(event: ReactMouseEvent<HTMLDivElement>): void {
    if (!suppressClick.current) {
      return;
    }
    suppressClick.current = false;
    event.stopPropagation();
    event.preventDefault();
  }

  useEffect(() => {
    const view = plane;
    if (view === null) {
      return undefined;
    }
    function zoomAtCursor(event: WheelEvent): void {
      if (view === null) {
        return;
      }
      event.preventDefault();
      const box = view.getBoundingClientRect();
      const pointerX = event.clientX - box.left;
      const pointerY = event.clientY - box.top;

      setZoom((current) => {
        const next = Math.min(
          1.5,
          Math.max(0.5, Math.round((current - Math.sign(event.deltaY) * 0.1) * 10) / 10),
        );
        if (next === current) {
          return current;
        }
        const contentX = (view.scrollLeft + pointerX) / current;
        const contentY = (view.scrollTop + pointerY) / current;
        anchor.current = {
          left: contentX * next - pointerX,
          top: contentY * next - pointerY,
        };
        return next;
      });
    }
    view.addEventListener('wheel', zoomAtCursor, { passive: false });
    return () => view.removeEventListener('wheel', zoomAtCursor);
  }, [plane]);

  useLayoutEffect(() => {
    const view = viewport.current;
    const to = anchor.current;
    if (view === null || to === null) {
      return;
    }
    anchor.current = null;
    view.scrollLeft = to.left;
    view.scrollTop = to.top;
  }, [zoom]);

  function toggle(state: CardState): void {
    const next = new Set(visible);
    if (next.has(state)) {
      next.delete(state);
    } else {
      next.add(state);
    }

    setVisible(next.size === 0 ? new Set(STATES) : next);
  }

  if (loading) {
    return (
      <p style={{ margin: 0, padding: 'var(--space-6)', color: 'var(--muted)' }}>
        {t('canvas.board.loading')}
      </p>
    );
  }

  if (sections.every((section) => section.bands.length === 0)) {
    return (
      <p style={{ margin: 0, padding: 'var(--space-6)', color: 'var(--muted)' }}>
        {t('canvas.board.empty')}
      </p>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0, flex: 1 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 'var(--space-3)',
          padding: 'var(--space-2) var(--space-6)',
          borderBlockEnd: '1px solid var(--line)',
          background: 'var(--surface)',
        }}
      >
        <span style={{ fontSize: 'var(--text-sm)', color: 'var(--faint)' }}>
          {t('canvas.board.summary', {
            count: sections.flatMap((section) => section.bands).filter((band) => band.running)
              .length,
          })}
        </span>
        <div style={{ marginInlineStart: 'auto', display: 'flex', gap: 'var(--space-1)' }}>
          {STATES.map((state) => (
            <button
              key={state}
              type="button"
              aria-pressed={visible.has(state)}
              onClick={() => toggle(state)}
              className="fo-legend-chip"
              style={{ opacity: visible.has(state) ? 1 : 0.45 }}
            >
              <span
                aria-hidden="true"
                style={{
                  width: '7px',
                  height: '7px',
                  borderRadius: '50%',
                  background: STATE_DOT[state],
                }}
              />
              {t(`canvas.board.state.${state}`)}
            </button>
          ))}
        </div>
      </div>

      <div
        ref={attachPlane}
        className="canvas-plane"
        onPointerDown={beginDrag}
        onPointerMove={moveDrag}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        onClickCapture={swallowClickAfterPan}
        style={{
          flex: 1,
          minHeight: 0,
          overflow: 'auto',
          position: 'relative',

          cursor: 'grab',
        }}
      >
        <div
          style={{
            width: natural.width * zoom + SLACK,
            height: natural.height * zoom + SLACK,
          }}
        >
          <div
            ref={content}
            style={{
              transform: `scale(${zoom})`,
              transformOrigin: 'top left',
              padding: 'var(--space-5) var(--space-6)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-6)',
              width: 'max-content',
            }}
          >
            {sections.map((section) => (
              <section key={section.key} aria-label={section.title}>
                <h2
                  style={{
                    margin: '0 0 var(--space-3)',
                    fontSize: 'var(--text-sm)',
                    fontWeight: 600,
                    letterSpacing: '0.04em',
                    textTransform: 'uppercase',
                    color: section.named ? 'var(--brand-dark)' : 'var(--faint)',
                  }}
                >
                  {section.title}
                </h2>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                  {section.bands.map((band) => (
                    <ProcessBand
                      key={band.id}
                      band={band}
                      locale={locale}
                      ownerName={ownerNames.get(band.ownerId)}
                      visible={visible}
                      onAddTask={onAddTask}
                      onOpenTask={onOpenTask}
                      onShowTip={showTip}
                      onHideTip={() => {
                        setTip(null);
                      }}
                      action={bandAction?.(band.id)}
                    />
                  ))}
                </div>
              </section>
            ))}
          </div>
        </div>

        <div className="fo-plane-float fo-plane-float-start">
          <button
            type="button"
            className="fo-plane-control"
            aria-label={t('canvas.board.zoomOut')}
            onClick={() => setZoom((z) => Math.max(0.5, Math.round((z - 0.1) * 10) / 10))}
          >
            <Icon name="minus" size={11} strokeWidth={1.6} />
          </button>

          <span
            style={{
              minWidth: '38px',
              textAlign: 'center',
              fontSize: 'var(--text-xs)',
              fontWeight: 500,
              color: 'var(--slate)',
            }}
          >
            {Math.round(zoom * 100)}%
          </span>
          <button
            type="button"
            className="fo-plane-control"
            aria-label={t('canvas.board.zoomIn')}
            onClick={() => setZoom((z) => Math.min(1.5, Math.round((z + 0.1) * 10) / 10))}
          >
            <Icon name="plus" size={11} strokeWidth={1.6} />
          </button>
          <span aria-hidden="true" className="fo-plane-divider" />
          <button
            type="button"
            className="fo-plane-control"
            onClick={() => {
              const view = viewport.current;
              if (view !== null && view.scrollWidth > 0) {
                const unscaled = view.scrollWidth / zoom;
                setZoom(Math.min(1, Math.max(0.5, view.clientWidth / unscaled)));
              }
            }}
          >
            <Icon name="fit" size={11} strokeWidth={1.4} />
            {t('canvas.board.fit')}
          </button>
          <button type="button" className="fo-plane-control" onClick={() => setZoom(1)}>
            <Icon name="reset" size={11} strokeWidth={1.4} />
            {t('canvas.board.reset')}
          </button>
        </div>

        <p className="fo-plane-hint">{t('canvas.board.hint')}</p>

        <StepCardTip tip={tip} />
      </div>
    </div>
  );
}

function ProcessBand({
  band,
  locale,
  ownerName,
  visible,
  onAddTask,
  onOpenTask,
  onShowTip,
  onHideTip,
  action,
}: {
  band: Band;
  locale: SupportedLocale;
  ownerName: string | undefined;
  visible: ReadonlySet<CardState>;
  onAddTask: (instanceId: string) => void;
  onOpenTask: (taskId: string) => void;

  onShowTip: (card: BandCard, element: HTMLElement) => void;
  onHideTip: () => void;

  action?: ReactNode;
}): JSX.Element {
  const { t } = useTranslation();
  const percent =
    band.progress.total === 0 ? 0 : (band.progress.closed / band.progress.total) * 100;

  return (
    <section
      aria-label={band.name}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 'var(--space-4)',
        padding: 'var(--space-4)',
        background: 'var(--surface)',
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius-card)',
      }}
    >
      <div style={{ width: '160px', flexShrink: 0 }}>
        <Link
          to={`/${locale}/canvas/process/${band.id}`}

          draggable={false}
          style={{
            font: '600 var(--text-md) var(--font-sans)',
            color: 'var(--ink)',
            textDecoration: 'none',
            letterSpacing: '-0.01em',
            display: 'block',
            marginBlockEnd: 'var(--space-2)',
          }}
        >
          {band.name}
        </Link>

        {action === undefined ? null : (
          <div style={{ marginBlockEnd: 'var(--space-2)' }}>{action}</div>
        )}
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '5px',
            fontSize: 'var(--text-xs)',
            fontWeight: 500,
            color: band.running ? 'var(--brand-dark)' : 'var(--done)',
            background: band.running ? 'var(--brand-soft)' : 'var(--done-soft)',
            borderRadius: 'var(--radius-chip)',
            padding: '2px var(--space-2)',
          }}
        >
          <span
            aria-hidden="true"
            style={{
              width: '6px',
              height: '6px',
              borderRadius: '50%',
              background: band.running ? 'var(--brand)' : 'var(--done)',
            }}
          />
          {band.running ? t('canvas.board.running') : t('canvas.board.complete')}
        </span>
        <p
          style={{
            margin: 'var(--space-3) 0 var(--space-1)',
            fontSize: 'var(--text-xs)',
            color: 'var(--faint)',
          }}
        >
          {t('canvas.board.progress', {
            closed: band.progress.closed,
            total: band.progress.total,
          })}
        </p>
        <div
          style={{
            height: '3px',
            width: '96px',
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
        <p
          style={{
            margin: 'var(--space-3) 0 0',
            fontSize: 'var(--text-sm)',
            color: 'var(--muted)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {ownerName ?? t('canvas.board.formerMember')}
        </p>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', minWidth: 0 }}>
        <span
          style={{
            fontSize: 'var(--text-xs)',
            fontWeight: 600,
            color: 'var(--faint)',
            border: '1px solid var(--line)',
            borderRadius: 'var(--radius-chip)',
            padding: '3px var(--space-2)',
            background: 'var(--bg)',
            flexShrink: 0,
          }}
        >
          {t('canvas.board.start')}
        </span>
        {band.cards.map((card) => (
          <StepCard
            key={card.id}
            card={card}
            dimmed={!visible.has(card.state)}
            onOpenTask={onOpenTask}
            onShowTip={onShowTip}
            onHideTip={onHideTip}
          />
        ))}
        <span aria-hidden="true" className="fo-band-connector" />
        <button
          type="button"
          className="fo-add-task-card"
          onClick={() => onAddTask(band.id)}
          disabled={!band.running}
        >
          {t('canvas.board.addTask')}
        </button>
      </div>
    </section>
  );
}

export function StepCard({
  card,
  dimmed,
  leader = true,
  onOpenTask,
  onShowTip,
  onHideTip,
}: {
  card: BandCard;
  dimmed: boolean;

  leader?: boolean;
  onOpenTask: (taskId: string) => void;

  onShowTip: (card: BandCard, element: HTMLElement) => void;
  onHideTip: () => void;
}): JSX.Element {
  const { t, i18n } = useTranslation();
  const taskId = card.taskId;

  const displayName =
    card.assigneeName === null
      ? ''
      : card.assigneeName === ''
        ? t('canvas.board.formerMember')
        : card.assigneeName;

  return (
    <>
      {leader ? <span aria-hidden="true" className="fo-band-connector" /> : null}
      <article
        data-state={card.state}

        onClick={taskId === null ? undefined : () => onOpenTask(taskId)}
        onKeyDown={
          taskId === null
            ? undefined
            : (event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onOpenTask(taskId);
                }
              }
        }
        role={taskId === null ? undefined : 'button'}
        tabIndex={taskId === null ? undefined : 0}
        className="fo-band-card"

        data-openable={taskId === null ? undefined : 'true'}

        onMouseEnter={(event) => onShowTip(card, event.currentTarget)}
        onMouseLeave={onHideTip}
        onFocus={(event) => onShowTip(card, event.currentTarget)}
        onBlur={onHideTip}
        style={
          {
            '--card-accent': STATE_DOT[card.state],

            '--card-fill': CARD_FILL[card.state],

            opacity: dimmed ? 0.62 : 1,
          } as CSSProperties
        }
      >
        <header
          style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', minWidth: 0 }}
        >
          <GlyphTile
            fill={CARD_TILE[card.state].fill}
            ink={STATE_DOT[card.state]}
            size={CARD_TILE_SIZE}
          >
            <TileGlyph size={CARD_TILE_SIZE}>{CARD_TILE[card.state].icon}</TileGlyph>
          </GlyphTile>
          <span
            style={{
              fontSize: 'var(--text-sm)',
              fontWeight: 600,
              color: 'var(--ink)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {card.title}
          </span>
        </header>

        {card.blockedReason !== null ? (
          <p
            style={{
              margin: 'var(--space-2) 0 0',
              fontSize: 'var(--text-xs)',
              color: 'var(--alert)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {card.blockedReason}
          </p>
        ) : null}

        <div
          data-testid="canvas-card-meta"
          style={{ marginTop: 'auto', display: 'grid', gap: '5px', paddingTop: 'var(--space-2)' }}
        >
          {card.assigneeName !== null && (
            <MetaCell label={t('canvas.board.assignee')}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '5px', minWidth: 0 }}>
                <Avatar name={displayName} id={card.assigneeId ?? undefined} size={16} />
                <span
                  data-testid="canvas-card-assignee"
                  style={{
                    fontSize: 'var(--text-xs)',
                    fontWeight: 500,
                    color: 'var(--slate)',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {displayName}
                </span>
              </span>
            </MetaCell>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '5px' }}>
            {card.deadline !== null && (
              <MetaCell label={t('canvas.node.due')}>
                <MetaValue
                  testId="canvas-card-due"
                  tone={card.state === 'done' ? undefined : card.atRisk ? 'warn' : undefined}
                >
                  {relativeLabel(new Date(card.deadline), new Date(), i18n.language)}
                </MetaValue>
              </MetaCell>
            )}

            <MetaCell label={t('canvas.board.stateLabel')}>
              <MetaValue tone={card.bottleneckMinutes !== null ? 'warn' : undefined}>
                {card.bottleneckMinutes !== null
                  ? t('canvas.board.waited', {
                      duration: durationLabel(card.bottleneckMinutes * 60, i18n.language),
                    })
                  : t(`canvas.board.state.${card.state}`)}
              </MetaValue>
            </MetaCell>
          </div>
        </div>
      </article>
    </>
  );
}

function MetaCell({ label, children }: { label: string; children: ReactNode }): JSX.Element {
  return (
    <div
      style={{
        minWidth: 0,
        padding: '4px 7px',
        borderRadius: 'var(--radius-sm)',
        background: 'color-mix(in srgb, var(--surface-sunk) 70%, transparent)',
      }}
    >
      <span
        style={{
          display: 'block',
          fontSize: '9px',
          lineHeight: 1.4,
          fontWeight: 500,
          letterSpacing: 'var(--tracking-eyebrow)',
          textTransform: 'uppercase',
          color: 'var(--faint)',
        }}
      >
        {label}
      </span>
      {children}
    </div>
  );
}

function MetaValue({
  children,
  tone,
  testId,
}: {
  children: ReactNode;
  tone?: 'warn';
  testId?: string;
}): JSX.Element {
  return (
    <span
      data-testid={testId}
      style={{
        display: 'block',
        fontSize: 'var(--text-xs)',
        fontWeight: 500,
        fontVariantNumeric: 'tabular-nums',
        color: tone === 'warn' ? 'var(--waiting)' : 'var(--slate)',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </span>
  );
}
