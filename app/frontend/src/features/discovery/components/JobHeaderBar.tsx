import { useState, type JSX } from 'react';

import type { JobHeader } from '../api/jobGraphApi';

interface JobHeaderBarProps {
  readonly header: JobHeader;

  readonly onClose?: () => void;

  readonly onForceClose?: (reason: string) => void;
}

export function JobHeaderBar({ header, onClose, onForceClose }: JobHeaderBarProps): JSX.Element {
  const closed = header.status === 'CLOSED';
  const forced = header.closeReason !== null;
  const live = header.liveBrackets;

  const empty = header.totalBrackets === 0;

  const [forcing, setForcing] = useState(false);
  const [reason, setReason] = useState('');

  return (
    <header className="fo-jobbar" data-status={header.status}>
      <p className="fo-jobbar-eyebrow">
        {header.client ?? 'Internal'}
        <span aria-hidden="true"> ▸ </span>
        {header.project ?? 'Unnamed project'}
      </p>

      <h2 className="fo-jobbar-title">{header.name}</h2>

      <div className="fo-jobbar-meta">
        <span className="fo-jobbar-state" data-forced={forced}>
          <span aria-hidden="true">
            {closed ? (forced ? '—' : '✓') : live === 0 && !empty ? '◆' : '◇'}
          </span>
          {closed
            ? forced
              ? 'Force-closed'
              : 'Closed'
            : empty
              ? 'Nothing marked yet'
              : live === 0
                ? 'Ready to close'
                : 'Open'}
        </span>

        {closed ? null : (
          <span className="fo-jobbar-live">
            {empty
              ? 'Mark a message to put work here'
              : live === 0
                ? 'Nothing still open'
                : `${String(live)} ${live === 1 ? 'piece of work' : 'pieces of work'} still open`}
          </span>
        )}

        {header.closerName === null ? null : (
          <span className="fo-jobbar-closer">Closes: {header.closerName}</span>
        )}

        {closed && !header.shapeEligible ? (
          <span className="fo-jobbar-hole">
            <span aria-hidden="true">▲</span>
            Not part of the learned shape
          </span>
        ) : null}
      </div>

      {forced ? <p className="fo-jobbar-scar">{header.closeReason}</p> : null}

      {closed ? null : (
        <div className="fo-jobbar-action">
          {live === 0 && !empty && onClose !== undefined ? (
            <button type="button" className="ui-button ui-button-accent" onClick={onClose}>
              Close this job
            </button>
          ) : null}

          {onForceClose === undefined ? null : forcing ? (
            <div className="fo-jobbar-forcing">
              <label className="fo-close-label" htmlFor="force-reason">
                {live === 0
                  ? 'Why is this being forced closed?'
                  : `This will close ${String(live)} live ${live === 1 ? 'piece of work' : 'pieces of work'}. Why?`}
              </label>
              <input
                id="force-reason"
                className="ui-control"
                type="text"
                value={reason}
                placeholder="The prospect never converted"
                onChange={(event) => {
                  setReason(event.target.value);
                }}
              />
              <button
                type="button"
                className="ui-button ui-button-accent"
                disabled={reason.trim() === ''}
                onClick={() => {
                  onForceClose(reason.trim());
                }}
              >
                Force close
              </button>
              <button
                type="button"
                className="ui-button ui-button-quiet"
                onClick={() => {
                  setForcing(false);
                }}
              >
                Cancel
              </button>
              <p className="fo-close-help">
                A forced ending keeps what was delivered and never teaches the business a shape.
              </p>
            </div>
          ) : (
            <button
              type="button"
              className="ui-button ui-button-quiet"
              onClick={() => {
                setForcing(true);
              }}
            >
              Force close
            </button>
          )}
        </div>
      )}
    </header>
  );
}
