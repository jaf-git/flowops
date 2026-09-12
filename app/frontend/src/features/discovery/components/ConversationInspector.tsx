import { useQuery } from '@tanstack/react-query';
import { useMemo, useState, type JSX } from 'react';

import { fetchJobsForConversation } from '../api/discoveryApi';
import { describeEnding } from '../model/bracket';
import { useJobGraph, useJobHeader } from '../hooks/useJobGraph';
import {
  useConversationMarks,
  useConversationWaits,
  useConversationWork,
} from '../hooks/useConversationWork';
import { useBracketReach } from '../hooks/useBracketReach';
import { useReadingMessage } from '../hooks/useReadingMessage';
import { DescribeWork } from './DescribeWork';

interface ConversationInspectorProps {
  readonly conversationId: string;

  readonly viewerId?: string;

  readonly nameOfConversation?: (conversationId: string) => string | undefined;
  readonly onOpenConversation?: (conversationId: string) => void;
  readonly onOpenGraph?: (jobId: string) => void;
}

export function ConversationInspector({
  conversationId,
  viewerId,
  nameOfConversation,
  onOpenConversation,
  onOpenGraph,
}: ConversationInspectorProps): JSX.Element | null {
  const [spanOpen, setSpanOpen] = useState(false);

  const jobs = useQuery({
    queryKey: ['discovery', 'jobs', 'describing', conversationId],
    queryFn: () => fetchJobsForConversation(conversationId, true),
  });

  const jobId = (jobs.data ?? [])[0]?.jobId;

  const header = useJobHeader(jobId);
  const graph = useJobGraph(jobId);
  const work = useConversationWork(conversationId);
  const waits = useConversationWaits(conversationId);

  const spans = useMemo(() => {
    const found = new Set<string>();

    for (const node of graph.data?.nodes ?? []) {
      if (node.conversationId !== null) {
        found.add(node.conversationId);
      }
    }

    found.add(conversationId);
    return [...found];
  }, [graph.data, conversationId]);

  const elsewhere = spans.filter((one) => one !== conversationId);

  const workTypesIn = useMemo(() => {
    const byConversation = new Map<string, Set<string>>();

    for (const node of graph.data?.nodes ?? []) {
      if (node.conversationId === null || node.boundary) {
        continue;
      }

      const seen = byConversation.get(node.conversationId) ?? new Set<string>();
      seen.add(node.workType);
      byConversation.set(node.conversationId, seen);
    }

    return byConversation;
  }, [graph.data]);

  const reading = useReadingMessage();
  const marks = useConversationMarks(conversationId);

  const readingMark = (marks.data ?? []).find(
    (mark) => reading !== null && mark.messageId === reading.messageId,
  );

  const readingBracket = (work.data ?? []).find(
    (bracket) => readingMark?.bracketId !== null && bracket.bracketId === readingMark?.bracketId,
  );

  const readingWaits = (waits.data ?? []).filter(
    (wait) => readingMark?.bracketId !== null && wait.bracketId === readingMark?.bracketId,
  );

  const reaches = useBracketReach(conversationId);
  const bracketReaches =
    readingMark?.bracketId === undefined || readingMark.bracketId === null
      ? []
      : (reaches.get(readingMark.bracketId) ?? []);

  const brackets = work.data ?? [];
  const mine = brackets.filter(
    (bracket) => bracket.live && viewerId !== undefined && bracket.performerId === viewerId,
  );
  const mineIds = new Set(mine.map((bracket) => bracket.bracketId));
  const blockingMe = (waits.data ?? []).filter((wait) => mineIds.has(wait.bracketId));

  if (jobId === undefined || header.data === undefined) {
    return null;
  }

  const job = header.data;

  return (
    <aside className="fo-inspector" aria-label="About this engagement">
      <section className="fo-inspector-card">
        <p className="fo-inspector-eyebrow">
          {job.client ?? 'Client not set'}
          {job.project === null ? '' : ` ▸ ${job.project}`}
        </p>

        {onOpenGraph === undefined ? (
          <h4 className="fo-inspector-title">{job.name}</h4>
        ) : (
          <h4 className="fo-inspector-title">
            <button
              type="button"
              className="fo-inspector-open"
              onClick={() => {
                onOpenGraph(jobId);
              }}
            >
              {job.name}
            </button>
          </h4>
        )}

        <p className="fo-inspector-payload">
          <span className="fo-inspector-state" data-state={job.status}>
            {job.status}
          </span>

          <span className="fo-inspector-count">
            {job.liveBrackets === 0 ? 'nothing live' : `${String(job.liveBrackets)} still live`}
          </span>
        </p>

        {elsewhere.length === 0 ? (
          <p className="fo-inspector-detail">
            All of this engagement&rsquo;s work is in this room.
          </p>
        ) : (
          <>
            <button
              type="button"
              className="fo-inspector-span"
              aria-expanded={spanOpen}
              onClick={() => {
                setSpanOpen((open) => !open);
              }}
            >
              {`Spans ${String(spans.length)} conversations`}
            </button>

            {spanOpen ? (
              <ul className="fo-inspector-rooms">
                {elsewhere.map((one) => {
                  const name = nameOfConversation?.(one);
                  const types = [...(workTypesIn.get(one) ?? [])];

                  return (
                    <li key={one}>
                      <button
                        type="button"
                        className="ui-button ui-button-quiet"
                        disabled={onOpenConversation === undefined}
                        onClick={() => {
                          onOpenConversation?.(one);
                        }}
                      >
                        {name ?? (types.length === 0 ? 'Another conversation' : types.join(', '))}
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : null}
          </>
        )}
      </section>

      {reading === null || (marks.data ?? []).length === 0 ? null : readingMark === undefined ? (
        <section className="fo-inspector-card">
          <h4 className="fo-inspector-title">This message&rsquo;s work</h4>
          <p className="fo-inspector-detail">
            Press the line beneath a marked message to see what became of it.
          </p>
        </section>
      ) : (
        <section className="fo-inspector-card">
          <p className="fo-inspector-eyebrow">{readingMark.jobName}</p>

          <h4 className="fo-inspector-title">
            {readingMark.boundary ? 'Opens this engagement' : readingMark.address}
          </h4>

          <p className="fo-inspector-payload">
            <span className="fo-inspector-state" data-state={readingMark.state}>
              {readingMark.closeKind === null
                ? readingMark.state
                : describeEnding(readingMark.closeKind).label}
            </span>
            {readingMark.performerName === null ? null : (
              <span className="fo-inspector-count">{readingMark.performerName}</span>
            )}
          </p>

          {readingMark.client === null && readingMark.project === null ? null : (
            <p className="fo-inspector-payload">
              {readingMark.client === null ? null : (
                <span className="fo-mark-tag">{readingMark.client}</span>
              )}
              {readingMark.project === null ? null : (
                <span className="fo-mark-tag">{readingMark.project}</span>
              )}
              <span className="fo-mark-tag" data-kind="type">
                {readingMark.workType}
              </span>
              {readingMark.activity === null ? null : (
                <span className="fo-mark-tag" data-kind="activity">
                  {readingMark.activity}
                </span>
              )}
            </p>
          )}

          {readingWaits.length === 0 ? (
            readingBracket?.live === true ? (
              <p className="fo-inspector-detail">Nothing is blocking it.</p>
            ) : null
          ) : (
            <ul className="fo-inspector-list">
              {readingWaits.map((wait) => (
                <li key={wait.waitId} className="fo-inspector-row">
                  <span aria-hidden="true">◇</span>
                  <span className="fo-inspector-what">
                    {wait.reason ?? wait.blockingAddress ?? wait.kind}
                  </span>
                  {wait.external ? <span className="fo-inspector-external">outside</span> : null}
                </li>
              ))}
            </ul>
          )}

          {bracketReaches.length === 0 ? null : (
            <p className="fo-inspector-detail">
              {(() => {
                const first = bracketReaches[0] as string;
                const name = nameOfConversation?.(first);
                const rest = bracketReaches.length - 1;

                if (name === undefined) {
                  return `This work is also in ${String(bracketReaches.length)} other ${
                    bracketReaches.length === 1 ? 'conversation' : 'conversations'
                  }.`;
                }

                return rest === 0 ? `Also in ${name}.` : `Also in ${name} + ${String(rest)}.`;
              })()}
            </p>
          )}

          {onOpenGraph === undefined ? null : (
            <button
              type="button"
              className="ui-button ui-button-quiet"
              onClick={() => {
                onOpenGraph(readingMark.jobId);
              }}
            >
              See it in the graph
            </button>
          )}

          {readingMark.boundary ? null : (
            <DescribeWork nodeId={readingMark.nodeId} title={readingMark.title} />
          )}
        </section>
      )}

      <section className="fo-inspector-card">
        <h4 className="fo-inspector-title">Your work here</h4>

        {viewerId === undefined ? (
          <p className="fo-inspector-detail">Signing in…</p>
        ) : mine.length === 0 ? (
          <p className="fo-inspector-detail">
            Nothing in this room is yours right now. Marking a message puts it here.
          </p>
        ) : (
          <ul className="fo-inspector-list">
            {mine.map((bracket) => (
              <li key={bracket.bracketId} className="fo-inspector-row">
                <span aria-hidden="true">{bracket.state === 'WAITING' ? '◇' : '◆'}</span>
                <span className="fo-inspector-what">{bracket.workType}</span>
              </li>
            ))}
          </ul>
        )}

        {blockingMe.length === 0 ? null : (
          <>
            <p className="fo-inspector-eyebrow">What is blocking it</p>
            <ul className="fo-inspector-list">
              {blockingMe.map((wait) => (
                <li key={wait.waitId} className="fo-inspector-row">
                  <span aria-hidden="true">◇</span>

                  <span className="fo-inspector-what">
                    {wait.reason ?? wait.blockingAddress ?? wait.kind}
                  </span>
                  {wait.external ? <span className="fo-inspector-external">outside</span> : null}
                </li>
              ))}
            </ul>
          </>
        )}
      </section>
    </aside>
  );
}
