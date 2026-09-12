import { useEffect, useState, type JSX } from 'react';

import type { WorkMatch } from '../api/jobGraphApi';
import { useWorkSearch } from '../hooks/useJobGraph';

interface WorkSearchProps {
  readonly onOpenMessage?: (conversationId: string, messageId: string) => void;
}

export function WorkSearch({ onOpenMessage }: WorkSearchProps): JSX.Element {
  const [typed, setTyped] = useState('');
  const [term, setTerm] = useState('');

  useEffect(() => {
    const settle = setTimeout(() => {
      setTerm(typed);
    }, 250);

    return () => {
      clearTimeout(settle);
    };
  }, [typed]);

  const found = useWorkSearch(term);
  const matches = found.data?.matches ?? [];
  const beyondReach = found.data?.beyondReach ?? 0;
  const asked = term.trim().length >= 2;

  return (
    <section className="fo-search">
      <label className="fo-search-label" htmlFor="fo-search-term">
        Search the work
      </label>
      <input
        id="fo-search-term"
        className="ui-control"
        type="search"
        value={typed}
        placeholder="A word somebody used"
        onChange={(event) => {
          setTyped(event.target.value);
        }}
      />

      {!asked ? (
        <p className="fo-search-help">
          Two letters or more. This looks through what people said that became work — not the whole
          conversation.
        </p>
      ) : found.isError ? (
        <p className="fo-search-help">
          That search could not be run. The words are still in their conversations.
        </p>
      ) : (
        <>
          {matches.length === 0 && beyondReach === 0 ? (
            <p className="fo-search-help">
              {`Nothing marked as work mentions “${term.trim()}”. Somebody may have said it without it becoming work.`}
            </p>
          ) : (
            <ul className="fo-search-results">
              {matches.map((match) => (
                <Result key={match.nodeId} match={match} onOpenMessage={onOpenMessage} />
              ))}
            </ul>
          )}

          {beyondReach === 0 ? null : (
            <p className="fo-search-beyond">
              {beyondReach === 1
                ? '1 more result is in a conversation you are not in.'
                : `${String(beyondReach)} more results are in conversations you are not in.`}
            </p>
          )}
        </>
      )}
    </section>
  );
}

function Result({
  match,
  onOpenMessage,
}: {
  match: WorkMatch;
  onOpenMessage?: (conversationId: string, messageId: string) => void;
}): JSX.Element {
  return (
    <li className="fo-search-row">
      <span className="fo-search-type">{match.workType}</span>

      <span className="fo-search-text">{match.text}</span>

      {onOpenMessage === undefined ? null : (
        <button
          type="button"
          className="ui-button ui-button-quiet"
          onClick={() => {
            onOpenMessage(match.conversationId, match.nodeId);
          }}
        >
          Open
        </button>
      )}
    </li>
  );
}
