import { useQuery } from '@tanstack/react-query';
import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { fetchDigest } from '../api/digestApi';
import { useMyWorkCounts } from '../hooks/useJobGraph';
import { discoveryKeys } from '../hooks/useDiscovery';

interface QueueCountersProps {
  readonly permissions: readonly string[];
}

export function QueueCounters({ permissions }: QueueCountersProps): JSX.Element | null {
  const { t } = useTranslation();
  const counts = useMyWorkCounts();

  const mayReadDigest = permissions.includes('DISCOVERY_CANVAS_VIEW');

  const digest = useQuery({
    queryKey: discoveryKeys.digest(),
    queryFn: fetchDigest,
    enabled: mayReadDigest,
    retry: false,
  });

  if (counts.data === undefined) {
    return null;
  }

  return (
    <div className="fo-queue-counters">
      <Tile
        glyph="▲"
        tone="warning"
        label={t('discovery.queue.waitingOnYou')}
        value={counts.data.waitingOnYou}
      />
      <Tile
        glyph="◇"
        tone="info"
        label={t('discovery.queue.openWork')}
        value={counts.data.openWork}
      />
      <Tile
        glyph="✓"
        tone="positive"
        label={t('discovery.queue.deliveredThisWeek')}
        value={counts.data.deliveredThisWeek}
      />
      {digest.data === undefined ? null : (
        <Tile
          glyph="◆"
          tone="series"
          label={t('discovery.queue.needsYourDecision')}
          value={digest.data.decisions.length}
        />
      )}
    </div>
  );
}

function Tile({
  glyph,
  tone,
  label,
  value,
}: {
  glyph: string;
  tone: string;
  label: string;
  value: number;
}): JSX.Element {
  return (
    <div className="fo-queue-tile" data-tone={tone}>
      <span className="fo-queue-tile-head">
        <span className="fo-queue-tile-swatch" aria-hidden="true">
          {glyph}
        </span>
        <span className="fo-queue-tile-label">{label}</span>
      </span>
      <span className="fo-queue-tile-value">{value}</span>
    </div>
  );
}
