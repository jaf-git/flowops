import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useState, type CSSProperties, type DragEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { staggerFor } from '../../../shared/motion/tokens';
import { EmptyState } from '../../../shared/ui/EmptyState';
import {
  fetchCanvas,
  moveCardToLane,
  type CanvasCard,
  type CanvasLane,
  type CanvasLoop,
} from '../api/canvasApi';
import { EndThread } from '../components/EndThread';
import { Refusal } from '../components/Refusal';
import { discoveryKeys, useJobsForConversation } from '../hooks/useDiscovery';

interface LaneOption {
  trackId: string;
  label: string;
}

type Translate = (key: string, vars?: Record<string, unknown>) => string;

function labelOf(lane: CanvasLane, t: Translate): string {
  return lane.fromRoleName !== null && lane.toRoleName !== null
    ? t('discovery.canvas.lane.pair', { from: lane.fromRoleName, to: lane.toRoleName })
    : t('discovery.canvas.lane.weaklyKeyed');
}

interface CanvasScreenProps {
  conversationId?: string;

  permissions: readonly string[];

  onFindWork?: () => void;

  onOpenMessage?: (conversationId: string, messageId: string) => void;
}

export function CanvasScreen({
  conversationId,
  permissions,
  onFindWork,
  onOpenMessage,
}: CanvasScreenProps): JSX.Element {
  const { t } = useTranslation();

  const jobs = useJobsForConversation(conversationId, true);
  const offered = jobs.data ?? [];

  const [picked, setPicked] = useState<string | undefined>(undefined);
  const guessed = offered.find((job) => job.guessed) ?? offered[0];
  const jobId = picked ?? guessed?.jobId;

  const canvas = useQuery({
    queryKey: discoveryKeys.canvas(jobId),
    queryFn: () => fetchCanvas(jobId as string),
    enabled: jobId !== undefined,

    retry: false,
  });

  const lanes = canvas.data?.lanes ?? [];
  const mayEnd = permissions.includes('WORK_NODE_MARK');

  const mayCorrect = permissions.includes('DISCOVERY_TYPE_CURATE');

  const queryClient = useQueryClient();
  const [refusal, setRefusal] = useState<string | undefined>(undefined);

  const [lifted, setLifted] = useState<string | undefined>(undefined);

  const move = useMutation({
    mutationFn: (order: { nodeId: string; trackId: string }) =>
      moveCardToLane(order.nodeId, order.trackId),

    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.canvasRoot });
    },
  });

  function moveCard(nodeId: string, trackId: string): void {
    setRefusal(undefined);
    setLifted(undefined);
    move.mutate(
      { nodeId, trackId },
      { onError: (failed) => setRefusal(failed instanceof ApiError ? failed.code : 'UNKNOWN') },
    );
  }

  const openLanes: LaneOption[] = lanes
    .filter((lane) => lane.closeReason === null)
    .map((lane) => ({ trackId: lane.trackId, label: labelOf(lane, t) }));

  return (
    <div className="fo-disc-canvas">
      <header className="fo-disc-canvas__head">
        <div className="fo-page-header-text">
          <h2 className="fo-page-title">{canvas.data?.jobName ?? t('discovery.canvas.title')}</h2>
          <p className="fo-page-subtitle">{t('discovery.canvas.lead')}</p>
        </div>

        {offered.length > 1 ? (
          <select
            className="ui-control"
            aria-label={t('discovery.canvas.job')}
            value={jobId ?? ''}
            onChange={(event) => setPicked(event.target.value)}

            style={{ maxWidth: '20rem' }}
          >
            {offered.map((job) => (
              <option key={job.jobId} value={job.jobId}>
                {job.name}
              </option>
            ))}
          </select>
        ) : null}
      </header>

      <Refusal code={refusal} />

      {canvas.isError ? (
        <EmptyState
          heading={t('discovery.canvas.cannotRead')}
          body={t('discovery.canvas.cannotReadBody')}
        />
      ) : jobId !== undefined && canvas.isPending ? (
        <p style={{ color: 'var(--muted)' }}>{t('discovery.canvas.loading')}</p>
      ) : lanes.length === 0 ? (
        <EmptyState
          heading={t('discovery.canvas.empty.heading')}
          body={t('discovery.canvas.empty.body')}
          action={
            onFindWork === undefined ? undefined : (
              <button type="button" className="ui-button ui-button-quiet" onClick={onFindWork}>
                {t('discovery.canvas.empty.action')}
              </button>
            )
          }
        />
      ) : (
        <div className="fo-disc-plane">
          {lanes.map((lane) => (
            <Lane
              key={lane.trackId}
              lane={lane}
              mayEnd={mayEnd}
              mayCorrect={mayCorrect}
              openLanes={openLanes}
              lifted={lifted}
              onLift={setLifted}
              onMove={moveCard}
              onOpenMessage={onOpenMessage}
            />
          ))}
        </div>
      )}
    </div>
  );
}

interface LaneProps {
  lane: CanvasLane;
  mayEnd: boolean;

  mayCorrect: boolean;

  openLanes: readonly LaneOption[];

  lifted: string | undefined;
  onLift: (nodeId: string | undefined) => void;
  onMove: (nodeId: string, trackId: string) => void;

  onOpenMessage?: (conversationId: string, messageId: string) => void;
}

function Lane({
  lane,
  mayEnd,
  mayCorrect,
  openLanes,
  lifted,
  onLift,
  onMove,
  onOpenMessage,
}: LaneProps): JSX.Element {
  const { t } = useTranslation();

  const named = lane.fromRoleName !== null && lane.toRoleName !== null;
  const label = labelOf(lane, t);

  const takesDrops = mayCorrect && lane.closeReason === null && lifted !== undefined;

  const titleOf = new Map(lane.cards.map((card) => [card.nodeId, card.title]));
  const cycling = new Set(lane.loops.flatMap((loop) => loop.memberNodeIds));

  const drawn: JSX.Element[] = [
    ...lane.cards
      .filter((card) => !cycling.has(card.nodeId))
      .map((card, along) => (
        <Card
          key={card.nodeId}
          card={card}
          along={along}
          mayCorrect={mayCorrect}

          elsewhere={openLanes.filter((option) => option.trackId !== lane.trackId)}
          onLift={onLift}
          onMove={onMove}
          onOpenMessage={onOpenMessage}
        />
      )),
    ...lane.loops.map((loop) => (
      <Loop key={loop.memberNodeIds.join('-')} loop={loop} titleOf={titleOf} />
    )),
  ];

  return (
    <section
      className="fo-disc-lane"
      aria-label={label}

      onDragOver={
        takesDrops ? (event: DragEvent<HTMLElement>) => event.preventDefault() : undefined
      }
      onDrop={
        takesDrops
          ? (event: DragEvent<HTMLElement>) => {
              event.preventDefault();
              const nodeId = event.dataTransfer.getData('text/plain');
              if (nodeId !== '') {
                onMove(nodeId, lane.trackId);
              }
            }
          : undefined
      }
      data-taking-drops={takesDrops ? 'true' : undefined}
    >
      <div className="fo-disc-lane__head">
        <div title={lane.weaklyKeyed ? t('discovery.canvas.lane.weaklyKeyedNote') : undefined}>
          {label}
        </div>

        {lane.weaklyKeyed && named ? (
          <div title={t('discovery.canvas.lane.weaklyKeyedNote')}>
            {t('discovery.canvas.lane.weaklyKeyed')}
          </div>
        ) : null}

        {lane.closeReason !== null ? (
          <div>
            {t(`discovery.canvas.closeReason.${lane.closeReason}`, {
              defaultValue: t('discovery.canvas.ended'),
            })}
          </div>
        ) : mayEnd ? (
          <EndThread trackId={lane.trackId} />
        ) : null}
      </div>

      {drawn.map((item, index) => (
        <Fragment key={item.key ?? index}>
          {index === 0 ? null : (
            <span className="fo-disc-edge" style={enteringAt(index)} aria-hidden="true">
              →
            </span>
          )}
          {item}
        </Fragment>
      ))}
    </section>
  );
}

function cardRole(card: {
  kind: CanvasCard['kind'];
  direction: CanvasCard['direction'];
}): 'JOB_START' | 'JOB_END' | 'COMPLETION' | undefined {
  if (card.kind === 'JOB_START' || card.kind === 'JOB_END') {
    return card.kind;
  }

  return card.direction === 'COMPLETION' ? 'COMPLETION' : undefined;
}

function enteringAt(along: number): CSSProperties {
  return { animationDelay: `${String(Math.round(staggerFor(along) * 1000))}ms` };
}

interface CardProps {
  card: CanvasCard;
  mayCorrect: boolean;

  along: number;

  elsewhere: readonly LaneOption[];
  onLift: (nodeId: string | undefined) => void;
  onMove: (nodeId: string, trackId: string) => void;

  onOpenMessage?: (conversationId: string, messageId: string) => void;
}

function Card({
  card,
  mayCorrect,
  along,
  elsewhere,
  onLift,
  onMove,
  onOpenMessage,
}: CardProps): JSX.Element {
  const { t } = useTranslation();

  const role = cardRole(card);

  const { conversationId, messageId } = card;
  const openMessage =
    onOpenMessage !== undefined && conversationId !== null && messageId !== null
      ? () => onOpenMessage(conversationId, messageId)
      : undefined;

  const correctable = mayCorrect && elsewhere.length > 0;

  return (
    <article
      style={enteringAt(along)}
      className={card.templated ? 'fo-disc-card fo-disc-card--templated' : 'fo-disc-card'}
      aria-label={card.title}
      draggable={correctable}
      onDragStart={
        correctable
          ? (event: DragEvent<HTMLElement>) => {
              event.dataTransfer.setData('text/plain', card.nodeId);
              event.dataTransfer.effectAllowed = 'move';
              onLift(card.nodeId);
            }
          : undefined
      }

      onDragEnd={correctable ? () => onLift(undefined) : undefined}
    >
      {role === undefined ? null : (
        <span className="fo-disc-card__role">{t(`discovery.canvas.card.role.${role}`)}</span>
      )}

      <span className="fo-disc-card__title">{card.title}</span>

      {card.outputType === null ? null : (
        <span>{t(`discovery.output.answer.${card.outputType}`)}</span>
      )}

      {card.phases.map((phase, index) => (
        <span className="fo-disc-phase" key={`${phase.phase}-${index}`}>
          {t('discovery.canvas.card.phaseDuration', {
            duration: readable(phase.ms),
            phase: t(`discovery.canvas.phase.${phase.phase}`),
          })}
        </span>
      ))}

      {card.templated ? <span>{t('discovery.canvas.card.templated')}</span> : null}

      {openMessage === undefined ? null : (
        <button
          type="button"
          className="fo-disc-card__open"
          aria-label={t('discovery.canvas.card.openMessageLabel', { title: card.title })}
          onClick={openMessage}
        >
          {t('discovery.canvas.card.openMessage')}
        </button>
      )}

      {correctable ? (
        <select
          className="ui-control"
          aria-label={t('discovery.canvas.move.label', { title: card.title })}
          value=""
          onChange={(event) => {
            if (event.target.value !== '') {
              onMove(card.nodeId, event.target.value);
            }
          }}

          style={{ maxWidth: '100%' }}
        >
          <option value="">{t('discovery.canvas.move.choose')}</option>
          {elsewhere.map((option) => (
            <option key={option.trackId} value={option.trackId}>
              {option.label}
            </option>
          ))}
        </select>
      ) : null}
    </article>
  );
}

function Loop({
  loop,
  titleOf,
}: {
  loop: CanvasLoop;
  titleOf: ReadonlyMap<string, string>;
}): JSX.Element {
  const { t } = useTranslation();

  const names = loop.memberNodeIds
    .map((nodeId) => titleOf.get(nodeId))
    .filter((title): title is string => title !== undefined);

  return (
    <div className="fo-disc-loop">
      <span>
        {t('discovery.canvas.card.loop', {
          first: names[0] ?? '',
          second: names[1] ?? names[0] ?? '',
        })}
      </span>

      <span className="fo-disc-loop__count">
        {t('discovery.canvas.card.loopCount', { count: loop.cycleCount })}
      </span>
    </div>
  );
}

function readable(ms: number): string {
  const hours = ms / 3_600_000;

  if (hours < 1) {
    return `${Math.max(1, Math.round(ms / 60_000))}m`;
  }

  return `${Number(hours.toFixed(1))}h`;
}
