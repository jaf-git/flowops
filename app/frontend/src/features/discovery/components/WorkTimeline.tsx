import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { describeEnding } from '../api/bracketApi';
import type { CloseKind } from '../model/bracket';
import type { TrackLine, TrackNode } from '../model/graph';
import { useMyTrack } from '../hooks/useJobGraph';

interface WorkTimelineProps {
  readonly onOpenMessage?: (conversationId: string, messageId: string) => void;
}

export function WorkTimeline({ onOpenMessage }: WorkTimelineProps): JSX.Element {
  const { t } = useTranslation();
  const track = useMyTrack();

  const lines = track.data ?? [];

  if (track.data !== undefined && lines.length === 0) {
    return (
      <section className="fo-track">
        <p className="fo-track-empty">{t('discovery.track.empty')}</p>
      </section>
    );
  }

  return (
    <section className="fo-track" aria-label={t('discovery.track.label')}>
      {lines.map((line) => (
        <Line key={line.bracketId} line={line} onOpenMessage={onOpenMessage} />
      ))}
    </section>
  );
}

function Line({
  line,
  onOpenMessage,
}: {
  line: TrackLine;
  onOpenMessage?: (conversationId: string, messageId: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  const ending = line.closeKind === null ? null : describeEnding(line.closeKind as CloseKind);

  return (
    <article className="fo-track-line" data-tone={toneOf(line, ending)}>
      <header className="fo-track-head">
        <span className="fo-track-address">{line.address}</span>

        <span className="fo-track-state">
          {ending === null
            ? line.state === 'WAITING'
              ? t('discovery.track.waiting')
              : t('discovery.work.open')
            : ending.label}
        </span>
      </header>

      <ol className="fo-track-chain">
        {line.nodes.map((node, index) => (
          <Circle
            key={node.nodeId}
            node={node}

            connected={index > 0}
            tone={toneOf(line, ending)}
            conversationId={line.conversationId}
            label={t(`discovery.track.node.${node.kind}`)}
            onOpenMessage={onOpenMessage}
          />
        ))}

        {ending === null || endsWithItsOwnNode(line) ? null : (
          <li className="fo-track-step" data-connected={line.nodes.length > 0}>
            <span
              className="fo-track-reach"
              role="img"
              aria-label={t('discovery.track.node.UNSPOKEN', { how: ending.label })}
              title={t('discovery.track.node.UNSPOKEN', { how: ending.label })}
            >
              <span
                className="fo-track-dot"
                data-kind="UNSPOKEN"
                data-tone={toneOf(line, ending)}
              />
            </span>
          </li>
        )}
      </ol>
    </article>
  );
}

function Circle({
  node,
  connected,
  tone,
  conversationId,
  label,
  onOpenMessage,
}: {
  node: TrackNode;
  connected: boolean;
  tone: string;
  conversationId: string;
  label: string;
  onOpenMessage?: (conversationId: string, messageId: string) => void;
}): JSX.Element {
  const inner = <span className="fo-track-dot" data-kind={node.kind} data-tone={tone} />;
  const reachable = node.messageId !== null && onOpenMessage !== undefined;

  return (
    <li className="fo-track-step" data-connected={connected}>
      {reachable ? (
        <button
          type="button"
          className="fo-track-reach"
          aria-label={label}
          title={label}
          onClick={() => {
            onOpenMessage(conversationId, node.messageId as string);
          }}
        >
          {inner}
        </button>
      ) : (
        <span className="fo-track-reach" aria-label={label} title={label} role="img">
          {inner}
        </span>
      )}
    </li>
  );
}

function endsWithItsOwnNode(line: TrackLine): boolean {
  return line.nodes.at(-1)?.kind === 'END';
}

function toneOf(line: TrackLine, ending: { completed: boolean } | null): string {
  if (ending !== null) {
    return ending.completed ? 'positive' : 'critical';
  }

  return line.state === 'WAITING' ? 'warning' : 'info';
}
