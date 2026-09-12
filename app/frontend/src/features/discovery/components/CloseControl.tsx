import { useState, type JSX } from 'react';

import type { OutputKind } from '../api/bracketApi';
import { useCloseBracket } from '../hooks/useConversationWork';
import { urlsIn } from '../model/urlInText';

interface CloseControlProps {
  readonly conversationId: string;
  readonly bracketId: string;

  readonly markedText?: string;
  readonly onClosed?: () => void;
}

type Chosen = 'DELIVERED' | 'DONE' | 'DROPPED';

export function CloseControl({
  conversationId,
  bracketId,
  markedText,
  onClosed,
}: CloseControlProps): JSX.Element {
  const found = urlsIn(markedText);

  const [chosen, setChosen] = useState<Chosen | null>(null);
  const [outputValue, setOutputValue] = useState(found[0] ?? '');
  const [reason, setReason] = useState('');

  const close = useCloseBracket(conversationId);

  const outputKind: OutputKind = outputValue.startsWith('http') ? 'LINK' : 'TEXT';

  const ready =
    chosen === 'DONE' ||
    (chosen === 'DELIVERED' && outputValue.trim() !== '') ||
    (chosen === 'DROPPED' && reason.trim() !== '');

  function submit(): void {
    if (chosen === null || !ready) {
      return;
    }

    close.mutate(
      {
        bracketId,
        request: {
          kind: chosen,
          ...(chosen === 'DELIVERED' ? { outputKind, outputValue: outputValue.trim() } : {}),
          ...(chosen === 'DROPPED' ? { reason: reason.trim() } : {}),
        },
      },
      { onSuccess: () => onClosed?.() },
    );
  }

  return (
    <div className="fo-close">
      <p className="fo-close-ask">Does this finish it?</p>

      <div className="fo-close-kinds" role="group" aria-label="How this ends">
        {(['DELIVERED', 'DONE', 'DROPPED'] as const).map((kind) => (
          <button
            key={kind}
            type="button"
            className="ui-chip"
            data-selected={chosen === kind}
            aria-pressed={chosen === kind}
            onClick={() => {
              setChosen(kind);
            }}
          >
            {kind === 'DELIVERED' ? 'Delivered' : kind === 'DONE' ? 'Done' : 'Dropped'}
          </button>
        ))}
      </div>

      {chosen === 'DELIVERED' ? (
        <div className="fo-close-follow">
          <label className="fo-close-label" htmlFor={`output-${bracketId}`}>
            What did it produce
          </label>

          {found.length < 2 ? null : (
            <div className="fo-close-urls">
              {found.map((url) => (
                <button
                  key={url}
                  type="button"
                  className="ui-chip"
                  data-selected={outputValue === url}
                  aria-pressed={outputValue === url}
                  onClick={() => {
                    setOutputValue(url);
                  }}
                >
                  {url}
                </button>
              ))}
            </div>
          )}

          <input
            id={`output-${bracketId}`}
            className="ui-control"
            type="text"
            value={outputValue}
            placeholder="A link, or what it was"
            onChange={(event) => {
              setOutputValue(event.target.value);
            }}
          />

          <p className="fo-close-help">
            A delivery names what it delivered. Without one, use Done.
          </p>
        </div>
      ) : null}

      {chosen === 'DROPPED' ? (
        <div className="fo-close-follow">
          <label className="fo-close-label" htmlFor={`reason-${bracketId}`}>
            Why it stopped
          </label>
          <input
            id={`reason-${bracketId}`}
            className="ui-control"
            type="text"
            value={reason}
            placeholder="Client cancelled the campaign"
            onChange={(event) => {
              setReason(event.target.value);
            }}
          />
          <p className="fo-close-help">
            A drop that does not say why teaches the business nothing.
          </p>
        </div>
      ) : null}

      {chosen === null ? null : (
        <button
          type="button"
          className="ui-button ui-button-accent"
          disabled={!ready || close.isPending}
          onClick={submit}
        >
          {close.isPending ? 'Closing…' : 'Close this work'}
        </button>
      )}

      {close.isError ? (
        <p className="fo-close-failed">
          That did not go through. The work is still open — try again, or check whether somebody
          else closed it.
        </p>
      ) : null}
    </div>
  );
}
