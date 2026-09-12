import { useQuery } from '@tanstack/react-query';
import type { JSX } from 'react';

import { apiRequest } from '../../../shared/api/client';
import type { RailLane } from '../model/graph';
import { appearanceOf } from '../model/nodeAppearance';

interface TrackerRailProps {
  readonly onOpenMessage?: (conversationId: string, messageId: string) => void;

  readonly currentJobId?: string;
  readonly onOpenJob?: (jobId: string) => void;
}

export function TrackerRail({
  onOpenMessage,
  currentJobId,
  onOpenJob,
}: TrackerRailProps): JSX.Element {
  const rail = useQuery({
    queryKey: ['discovery', 'rail'],
    queryFn: () => apiRequest<RailLane[]>('/discovery/graph/rail'),
  });

  if (rail.isPending) {
    return <p className="fo-rail-quiet">Reading what is open…</p>;
  }

  if (rail.isError) {
    return (
      <p className="fo-rail-quiet">The rail could not be read. The graph beside it still works.</p>
    );
  }

  const lanes = rail.data ?? [];

  if (lanes.length === 0) {
    return (
      <div className="fo-rail-quiet">
        <p className="fo-rail-heading">Open work</p>
        <p>Nothing is open. Every piece of work here has finished.</p>
        <p>Work appears here the moment somebody marks a message as theirs.</p>
      </div>
    );
  }

  return (
    <nav className="fo-tracker" aria-label="Open work">
      <p className="fo-rail-heading">Open work</p>
      {lanes.map((lane) => (
        <section
          key={lane.jobId}
          className="fo-tracker-lane"
          data-current={lane.jobId === currentJobId}
        >
          <p className="fo-tracker-address">
            {lane.client ?? 'Internal'}
            <span aria-hidden="true"> ▸ </span>
            {lane.project ?? 'Unnamed project'}
          </p>

          <button
            type="button"
            className="fo-tracker-job"
            aria-current={lane.jobId === currentJobId ? 'true' : undefined}
            onClick={() => {
              onOpenJob?.(lane.jobId);
            }}
          >
            {lane.jobName}
          </button>

          <ul className="fo-tracker-marks">
            {lane.marks.map((mark) => {
              const seen = appearanceOf({
                state: mark.state,
                closeKind: null,
                boundary: false,
              });

              return (
                <li key={mark.bracketId}>
                  <button
                    type="button"
                    className="fo-tracker-mark"
                    data-state={seen.state}
                    disabled={mark.messageId === null || onOpenMessage === undefined}
                    aria-label={`${mark.workType} · ${mark.performerName ?? 'Unclaimed'} · ${seen.label}`}
                    title={`${mark.workType} · ${seen.label}`}
                    onClick={() => {
                      if (mark.messageId !== null) {
                        onOpenMessage?.(mark.conversationId, mark.messageId);
                      }
                    }}
                  >
                    <span aria-hidden="true">{seen.glyph}</span>
                    <span className="fo-tracker-what">{mark.workType}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </nav>
  );
}
