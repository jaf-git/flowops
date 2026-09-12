import { useState, type JSX } from 'react';

import type { ConversationBracket, WaitKind } from '../api/bracketApi';
import { useDeclareWait } from '../hooks/useConversationWork';

interface WaitControlProps {
  readonly conversationId: string;
  readonly bracketId: string;

  readonly candidates: readonly ConversationBracket[];
  readonly onDeclared?: () => void;
}

const KINDS: ReadonlyArray<{
  id: WaitKind;
  label: string;
  external: boolean;
  expectedInDays: number;
}> = [
  { id: 'CLIENT', label: 'A client', external: true, expectedInDays: 5 },
  { id: 'SUPPLIER', label: 'A supplier', external: true, expectedInDays: 5 },
  { id: 'COLLEAGUE', label: 'A colleague', external: false, expectedInDays: 2 },
  { id: 'APPROVAL', label: 'An approval', external: false, expectedInDays: 2 },
];

function inDays(days: number): string {
  const when = new Date();
  when.setDate(when.getDate() + days);
  return `${String(when.getFullYear())}-${String(when.getMonth() + 1).padStart(2, '0')}-${String(when.getDate()).padStart(2, '0')}`;
}

export function WaitControl({
  conversationId,
  bracketId,
  candidates,
  onDeclared,
}: WaitControlProps): JSX.Element {
  const [kind, setKind] = useState<WaitKind>('COLLEAGUE');
  const [targets, setTargets] = useState<readonly string[]>([]);
  const [reason, setReason] = useState('');
  const [expectedBy, setExpectedBy] = useState('');
  const [satisfiedAlready, setSatisfiedAlready] = useState<number | null>(null);

  const declare = useDeclareWait(conversationId);

  const offerable = candidates.filter((one) => one.bracketId !== bracketId && one.live);
  const external = KINDS.find((one) => one.id === kind)?.external ?? false;

  const suggested = inDays(KINDS.find((one) => one.id === kind)?.expectedInDays ?? 2);
  const shownDate = expectedBy === '' ? suggested : expectedBy;

  function toggle(target: string): void {
    setTargets((chosen) =>
      chosen.includes(target) ? chosen.filter((one) => one !== target) : [...chosen, target],
    );
  }

  async function submit(): Promise<void> {
    const each = targets.length === 0 ? [undefined] : targets;
    let satisfied = 0;

    try {
      for (const target of each) {
        const answer = await declare.mutateAsync({
          bracketId,
          request: {
            kind,
            ...(target === undefined ? {} : { onBracketId: target }),
            ...(reason.trim() === '' ? {} : { reason: reason.trim() }),

            expectedBy: new Date(`${shownDate}T00:00:00`).toISOString(),
          },
        });

        if (answer.alreadySatisfied) {
          satisfied += 1;
        }
      }
    } catch {
      return;
    }

    setSatisfiedAlready(satisfied);
    onDeclared?.();
  }

  return (
    <div className="fo-wait">
      <p className="fo-close-ask">What are you waiting on?</p>

      <div className="fo-close-kinds" role="group" aria-label="Kind of wait">
        {KINDS.map((one) => (
          <button
            key={one.id}
            type="button"
            className="ui-chip"
            data-selected={kind === one.id}
            aria-pressed={kind === one.id}
            onClick={() => {
              setKind(one.id);
            }}
          >
            {one.label}
          </button>
        ))}
      </div>

      {external ? (
        <p className="fo-close-help">
          Time spent waiting on somebody outside the workspace is never counted against anybody
          here.
        </p>
      ) : null}

      {offerable.length === 0 ? null : (
        <div className="fo-wait-targets">
          <p className="fo-close-label">Waiting on which work? Pick as many as apply.</p>
          <div className="fo-close-urls">
            {offerable.map((one) => (
              <button
                key={one.bracketId}
                type="button"
                className="ui-chip"
                data-selected={targets.includes(one.bracketId)}
                aria-pressed={targets.includes(one.bracketId)}
                onClick={() => {
                  toggle(one.bracketId);
                }}
              >
                {one.address}
              </button>
            ))}
          </div>
          <p className="fo-close-help">
            Leave this empty when the thing you are waiting for is not in the graph — a
            client&rsquo;s answer, a delivery, a budget.
          </p>
        </div>
      )}

      <label className="fo-close-label" htmlFor={`wait-reason-${bracketId}`}>
        In your own words
      </label>
      <input
        id={`wait-reason-${bracketId}`}
        className="ui-control"
        type="text"
        value={reason}
        placeholder="Budget number not confirmed"
        onChange={(event) => {
          setReason(event.target.value);
        }}
      />

      <label className="fo-close-label" htmlFor={`wait-by-${bracketId}`}>
        Expected by
      </label>
      <input
        id={`wait-by-${bracketId}`}
        className="ui-control"
        type="date"
        value={shownDate}
        onChange={(event) => {
          setExpectedBy(event.target.value);
        }}
      />

      <p className="fo-close-help">
        Suggested for this kind of wait. Change it or leave it — nothing is enforced, and you are
        told when it passes.
      </p>

      <button
        type="button"
        className="ui-button ui-button-accent"
        disabled={declare.isPending}
        onClick={() => {
          void submit();
        }}
      >
        {declare.isPending ? 'Declaring…' : 'I am waiting on this'}
      </button>

      {satisfiedAlready === null ? null : (
        <p className="fo-close-help">
          {satisfiedAlready === 0
            ? `${String(Math.max(targets.length, 1))} declared, 0 cleared so far.`
            : `${String(satisfiedAlready)} of ${String(Math.max(targets.length, 1))} had already arrived and cleared straight away.`}
        </p>
      )}

      {declare.isError ? (
        <p className="fo-close-failed">
          That wait was refused. A wait cannot point at your own work, or at work in another
          engagement.
        </p>
      ) : null}
    </div>
  );
}
