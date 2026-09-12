import { useState, type JSX } from 'react';

import type { ConversationBracket, HandOverResponse } from '../api/bracketApi';
import { useHandOver } from '../hooks/useConversationWork';

export interface HandoverCandidate {
  readonly id: string;
  readonly displayName: string;
}

interface HandOverControlProps {
  readonly conversationId: string;
  readonly bracket: ConversationBracket;
  readonly candidates: readonly HandoverCandidate[];
  readonly onHandedOver?: () => void;
}

export function HandOverControl({
  conversationId,
  bracket,
  candidates,
  onHandedOver,
}: HandOverControlProps): JSX.Element {
  const [successor, setSuccessor] = useState<string | null>(null);
  const [becauseTheyLeft, setBecauseTheyLeft] = useState(false);
  const [done, setDone] = useState<HandOverResponse | null>(null);
  const [orphaned, setOrphaned] = useState(false);

  const handOver = useHandOver(conversationId);

  const offerable = candidates.filter((one) => one.id !== bracket.performerId);

  const announcement = bracket.messageIds[bracket.messageIds.length - 1];

  async function submit(): Promise<void> {
    if (successor === null || announcement === undefined) {
      return;
    }

    let outcome: HandOverResponse | undefined;

    try {
      outcome = await handOver.mutateAsync({
        bracketId: bracket.bracketId,
        request: {
          newPerformerId: successor,
          messageId: announcement,
          causedByDeactivation: becauseTheyLeft,
        },
      });
    } catch {
      return;
    }

    if (outcome === undefined) {
      setOrphaned(true);
    } else {
      setDone(outcome);
    }

    onHandedOver?.();
  }

  if (orphaned) {
    return (
      <div className="fo-handover">
        <p className="fo-close-help">
          That work is no longer held by anybody — the person you picked is no longer an active
          member. It is waiting to be placed, and anybody can take it on from the shelf.
        </p>
      </div>
    );
  }

  if (done !== null) {
    return (
      <div className="fo-handover">
        <p className="fo-close-help">
          {`${offerable.find((one) => one.id === successor)?.displayName ?? 'They'} now holds this work, still as ${done.workType}.`}
        </p>
        <p className="fo-close-help">
          {done.reTargeted === 0
            ? 'Nobody was waiting on it.'
            : `${String(done.reTargeted)} ${done.reTargeted === 1 ? 'colleague was' : 'colleagues were'} waiting on it, and ${done.reTargeted === 1 ? 'is' : 'are'} now waiting on them. Nobody was told it had arrived.`}
        </p>
        {done.disrupted ? (
          <p className="fo-close-help">
            This one is set aside from what the workspace learns about how long work takes. Time
            lost because somebody left is not evidence about the work.
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="fo-handover">
      <p className="fo-close-ask">Who is taking this on?</p>

      {offerable.length === 0 ? (
        <p className="fo-close-help">
          There is nobody else active to hand this to. Somebody has to join the workspace before
          work can move.
        </p>
      ) : (
        <div className="fo-close-urls" role="group" aria-label="Who is taking this on">
          {offerable.map((one) => (
            <button
              key={one.id}
              type="button"
              className="ui-chip"
              data-selected={successor === one.id}
              aria-pressed={successor === one.id}
              onClick={() => {
                setSuccessor(one.id);
              }}
            >
              {one.displayName}
            </button>
          ))}
        </div>
      )}

      <p className="fo-close-label">Why is it moving?</p>
      <div className="fo-close-kinds" role="group" aria-label="Why it is moving">
        <button
          type="button"
          className="ui-chip"
          data-selected={!becauseTheyLeft}
          aria-pressed={!becauseTheyLeft}
          onClick={() => {
            setBecauseTheyLeft(false);
          }}
        >
          The work is moving
        </button>
        <button
          type="button"
          className="ui-chip"
          data-selected={becauseTheyLeft}
          aria-pressed={becauseTheyLeft}
          onClick={() => {
            setBecauseTheyLeft(true);
          }}
        >
          They are no longer here
        </button>
      </div>

      <p className="fo-close-help">
        {becauseTheyLeft
          ? 'This one will be left out of what the workspace learns about how long work takes.'
          : 'How long each person held it is kept separately, so neither duration is lost in the other.'}
      </p>

      <button
        type="button"
        className="ui-button ui-button-accent"
        disabled={successor === null || announcement === undefined || handOver.isPending}
        onClick={() => {
          void submit();
        }}
      >
        {handOver.isPending ? 'Handing over…' : 'Hand this work over'}
      </button>

      {announcement === undefined ? (
        <p className="fo-close-help">
          This work has no message behind it, so there is nothing for the new work to start from.
        </p>
      ) : null}

      {handOver.isError ? (
        <p className="fo-close-failed">
          That handover was refused. The work may already have ended, or the person may have left
          the workspace since this list was drawn.
        </p>
      ) : null}
    </div>
  );
}
