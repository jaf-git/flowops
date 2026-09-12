import { useMemo, useState, type JSX } from 'react';

import type { ClientArtifact, GraphNode } from '../api/jobGraphApi';
import { JobEmptyState } from '../components/JobEmptyState';
import { JobGraph } from '../components/JobGraph';
import { JobHeaderBar } from '../components/JobHeaderBar';
import { JobJourney } from '../components/JobJourney';
import { TrackerRail } from '../components/TrackerRail';
import { useJobsForConversation } from '../hooks/useDiscovery';
import { useCloseJob, useJobArtifacts, useJobGraph, useJobHeader } from '../hooks/useJobGraph';
import { appearanceOf, elapsedWithPhase, performerOf } from '../model/nodeAppearance';
import type { LayoutView } from '../model/graphLayout';

interface JobGraphScreenProps {
  readonly conversationId: string | undefined;

  readonly focusJobId?: string;
  readonly onOpenConversation?: () => void;
  readonly onFindWork?: () => void;

  readonly onOpenMessage?: (conversationId: string, messageId: string) => void;
}

const VIEWS: ReadonlyArray<{ id: LayoutView; label: string }> = [
  { id: 'FLOW', label: 'Flow' },
  { id: 'CLUSTERS', label: 'Clusters' },
  { id: 'LANES', label: 'Lanes' },
];

type Reading = 'PLANE' | 'JOURNEY';

const READINGS: ReadonlyArray<{ id: Reading; label: string }> = [
  { id: 'PLANE', label: 'Plane' },
  { id: 'JOURNEY', label: 'Journey' },
];

export function JobGraphScreen({
  conversationId,
  onOpenConversation,
  onFindWork,
  onOpenMessage,
}: JobGraphScreenProps): JSX.Element {
  const [view, setView] = useState<LayoutView>('FLOW');
  const [reading, setReading] = useState<Reading>('PLANE');
  const [focus, setFocus] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [chosenJob, setChosenJob] = useState<string | null>(null);

  const jobs = useJobsForConversation(conversationId, true);

  const available = jobs.data ?? [];
  const jobId = chosenJob ?? available[0]?.jobId;

  const graph = useJobGraph(jobId);
  const header = useJobHeader(jobId);
  const artifacts = useJobArtifacts(jobId);
  const closing = useCloseJob(jobId ?? '');

  const nodes = useMemo(() => graph.data?.nodes ?? [], [graph.data]);
  const edges = useMemo(() => graph.data?.edges ?? [], [graph.data]);

  const work = useMemo(() => nodes.filter((node) => !node.boundary), [nodes]);
  const blocked = useMemo(() => work.filter((node) => node.state === 'WAITING'), [work]);
  const chosen = useMemo(
    () => nodes.find((node) => node.nodeId === selected) ?? null,
    [nodes, selected],
  );

  if (conversationId !== undefined && jobs.isPending) {
    return <p className="fo-jobgraph-quiet">Finding this conversation&rsquo;s engagements…</p>;
  }

  if (jobId === undefined) {
    return (
      <div className="fo-jobgraph">
        <div className="fo-jobgraph-body">
          <aside className="fo-jobgraph-rail">
            <TrackerRail
              onOpenMessage={onOpenMessage}
              onOpenJob={(id) => {
                setChosenJob(id);
                setSelected(null);
              }}
            />
          </aside>

          <div className="fo-jobgraph-plane">
            <div className="fo-jobgraph-quiet">
              <h3>No engagement open</h3>
              <p>
                Pick one from the rail, or start a new one from something somebody said in a
                conversation.
              </p>
              {onFindWork === undefined ? null : (
                <button type="button" className="ui-button ui-button-accent" onClick={onFindWork}>
                  Find a conversation
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (graph.isPending || header.isPending) {
    return <p className="fo-jobgraph-quiet">Drawing the engagement…</p>;
  }

  if (graph.isError || header.isError || header.data === undefined) {
    return (
      <p className="fo-jobgraph-quiet">
        This engagement could not be read. It may have been closed and removed — open the
        conversation it came from to check.
      </p>
    );
  }

  return (
    <div className="fo-jobgraph">
      <JobHeaderBar
        header={header.data}
        onClose={() => {
          closing.mutate({});
        }}
        onForceClose={(reason: string) => {
          closing.mutate({ reason });
        }}
      />

      {closing.isError ? (
        <p className="fo-jobgraph-quiet">
          That engagement could not be ended. It may still hold live work, or somebody may have
          ended it already.
        </p>
      ) : null}

      <div className="fo-jobgraph-body">
        <aside className="fo-jobgraph-rail">
          <TrackerRail
            currentJobId={jobId}
            onOpenMessage={onOpenMessage}
            onOpenJob={(id) => {
              setChosenJob(id);
              setSelected(null);
            }}
          />
        </aside>

        <div className="fo-jobgraph-plane">
          <div className="fo-jobgraph-bar">
            {available.length < 2 ? null : (
              <div className="fo-jobgraph-views" role="group" aria-label="Engagement">
                {available.map((one) => (
                  <button
                    key={one.jobId}
                    type="button"
                    className="ui-chip"
                    data-selected={one.jobId === jobId}
                    aria-pressed={one.jobId === jobId}
                    onClick={() => {
                      setChosenJob(one.jobId);
                      setSelected(null);
                    }}
                  >
                    {one.name}
                  </button>
                ))}
              </div>
            )}

            <div className="fo-jobgraph-views" role="group" aria-label="Reading">
              {READINGS.map((one) => (
                <button
                  key={one.id}
                  type="button"
                  className="ui-chip"
                  data-selected={reading === one.id}
                  aria-pressed={reading === one.id}
                  onClick={() => {
                    setReading(one.id);
                  }}
                >
                  {one.label}
                </button>
              ))}
            </div>

            {reading === 'JOURNEY' ? null : (
              <>
                <div className="fo-jobgraph-views" role="group" aria-label="Arrangement">
                  {VIEWS.map((one) => (
                    <button
                      key={one.id}
                      type="button"
                      className="ui-chip"
                      data-selected={view === one.id}
                      aria-pressed={view === one.id}
                      onClick={() => {
                        setView(one.id);
                      }}
                    >
                      {one.label}
                    </button>
                  ))}
                </div>

                <button
                  type="button"
                  className="ui-chip"
                  data-selected={focus}
                  aria-pressed={focus}
                  disabled={selected === null}
                  onClick={() => {
                    setFocus((on) => !on);
                  }}
                >
                  {focus ? 'Focus on' : 'Focus'}
                </button>
              </>
            )}
          </div>

          {work.length === 0 ? (
            <JobEmptyState header={header.data} onOpenConversation={onOpenConversation} />
          ) : reading === 'JOURNEY' ? (
            <JobJourney
              nodes={nodes}
              edges={edges}
              selected={selected}
              onSelect={(nodeId) => {
                setSelected(nodeId);
              }}
              onOpenMessage={onOpenMessage}
            />
          ) : (
            <JobGraph
              nodes={nodes}
              edges={edges}
              view={view}
              focus={focus}
              selected={selected}
              onSelect={(nodeId) => {
                setSelected(nodeId);
              }}
            />
          )}
        </div>
      </div>

      <div className="fo-jobgraph-drawer">
        <SelectionPanel node={chosen} onOpenMessage={onOpenMessage} />

        <section className="fo-jobgraph-panel">
          <h4 className="fo-jobgraph-panel-title">What is blocked</h4>

          {blocked.length === 0 ? (
            <p className="fo-jobgraph-panel-body">
              Nothing is waiting on anything. That is a good answer.
            </p>
          ) : (
            <ul className="fo-jobgraph-blocked">
              {[...blocked]

                .sort((one, other) => other.elapsed - one.elapsed)
                .map((node) => (
                  <li key={node.nodeId} className="fo-jobgraph-blocked-row">
                    <span aria-hidden="true">◇</span>
                    <span className="fo-jobgraph-blocked-what">{node.workType}</span>
                    <span className="fo-jobgraph-blocked-when">{elapsedWithPhase(node)}</span>
                  </li>
                ))}
            </ul>
          )}
        </section>

        <ArtifactPanel artifacts={artifacts.data ?? []} />

        <section className="fo-jobgraph-panel">
          <h4 className="fo-jobgraph-panel-title">This engagement</h4>
          <p className="fo-jobgraph-panel-body">
            {`${String(work.length)} ${work.length === 1 ? 'piece of work' : 'pieces of work'}, ${String(edges.length)} ${edges.length === 1 ? 'link' : 'links'}.`}
          </p>
          <p className="fo-jobgraph-panel-body">
            {`${String(work.filter((node) => appearanceOf(node).completed).length)} arrived, ${String(work.filter((node) => appearanceOf(node).state === 'SCAR').length)} ended another way.`}
          </p>
        </section>
      </div>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string | null }): JSX.Element | null {
  if (value === null || value.trim() === '') {
    return null;
  }

  return (
    <>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </>
  );
}

function SelectionPanel({
  node,
  onOpenMessage,
}: {
  node: GraphNode | null;
  onOpenMessage?: (conversationId: string, messageId: string) => void;
}): JSX.Element {
  if (node === null) {
    return (
      <section className="fo-jobgraph-panel">
        <h4 className="fo-jobgraph-panel-title">Nothing selected</h4>
        <p className="fo-jobgraph-panel-body">
          Pick a piece of work to see what it is and what it touches.
        </p>
      </section>
    );
  }

  const seen = appearanceOf(node);

  return (
    <section className="fo-jobgraph-panel" data-state={seen.state}>
      <p className="fo-jobgraph-panel-eyebrow">{performerOf(node)}</p>
      <h4 className="fo-jobgraph-panel-title">{node.workType}</h4>

      <p className="fo-jobgraph-panel-state">
        <span aria-hidden="true">{seen.glyph}</span>
        {seen.label}
      </p>

      <p className="fo-jobgraph-panel-body">{elapsedWithPhase(node)}</p>

      <dl className="fo-jobgraph-facts">
        <Fact label="Activity" value={node.activity} />
        <Fact label="Who did it" value={node.unclaimed ? 'Nobody yet' : node.performerName} />

        <Fact label="Who marked it" value={node.markerName} />
        <Fact label="Department" value={node.department} />
        <Fact label="Client" value={node.client} />
        <Fact label="Project" value={node.projectLabel} />
        <Fact label="Direction" value={node.direction} />
        <Fact label="Produced" value={node.outputType} />
      </dl>

      {node.workTypeOverridden ? (
        <p className="fo-jobgraph-panel-note">
          Somebody typed this work type rather than taking the one from their role, so it may not
          group with work that is really the same.
        </p>
      ) : null}

      {node.title === null ? null : (
        <p className="fo-jobgraph-panel-body">
          <strong>{node.title}</strong>
        </p>
      )}

      {node.detail === null ? null : <p className="fo-jobgraph-panel-body">{node.detail}</p>}

      {node.checklist === null ? null : node.checklist.length === 0 ? (
        <p className="fo-jobgraph-panel-note">No steps — somebody said so deliberately.</p>
      ) : (
        <ol className="fo-jobgraph-steps">
          {node.checklist.map((step, index) => (
            <li key={`${node.nodeId}-step-${String(index)}`}>{step}</li>
          ))}
        </ol>
      )}

      {node.text === null ? null : (
        <blockquote className="fo-jobgraph-said">{node.text}</blockquote>
      )}

      {node.messageId === null ||
      node.conversationId === null ||
      onOpenMessage === undefined ? null : (
        <button
          type="button"
          className="ui-button ui-button-quiet"
          onClick={() => {
            onOpenMessage(node.conversationId as string, node.messageId as string);
          }}
        >
          Open what was said
        </button>
      )}
    </section>
  );
}

function ArtifactPanel({ artifacts }: { artifacts: readonly ClientArtifact[] }): JSX.Element {
  return (
    <section className="fo-jobgraph-panel">
      <h4 className="fo-jobgraph-panel-title">Delivered</h4>

      {artifacts.length === 0 ? (
        <p className="fo-jobgraph-panel-body">
          Nothing has been published yet. A delivery names what it delivered, and that is what
          appears here.
        </p>
      ) : (
        <ul className="fo-jobgraph-blocked">
          {artifacts.map((one) => (
            <li key={one.artifactId} className="fo-jobgraph-blocked-row">
              <span aria-hidden="true">✓</span>
              <span className="fo-jobgraph-blocked-what">
                {one.workType}
                {one.readable && one.value !== null ? (
                  one.kind === 'LINK' ? (
                    <>
                      {' · '}
                      <a href={one.value} target="_blank" rel="noreferrer">
                        {one.value}
                      </a>
                    </>
                  ) : one.kind === 'MESSAGE_REF' ? (
                    <span className="fo-jobgraph-panel-note"> · said in the thread</span>
                  ) : (
                    <> · {one.value}</>
                  )
                ) : (
                  <span className="fo-jobgraph-panel-note">
                    {' · '}in a conversation you are not in
                  </span>
                )}
              </span>
              <time className="fo-jobgraph-blocked-when" dateTime={one.publishedAt}>
                {new Date(one.publishedAt).toLocaleDateString()}
              </time>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
