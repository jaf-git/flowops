import type { JSX } from 'react';

import { BlockedQuestion } from './BlockedQuestion';
import { DescribeWork } from './DescribeWork';
import { EndThread } from './EndThread';
import { OutputQuestion } from './OutputQuestion';

interface NodeQuestionsProps {
  nodeId: string;

  trackId: string | null;
}

export function NodeQuestions({ nodeId, trackId }: NodeQuestionsProps): JSX.Element {
  return (
    <div className="fo-disc-questions">
      <OutputQuestion nodeId={nodeId} />
      <BlockedQuestion nodeId={nodeId} />

      <DescribeWork nodeId={nodeId} />
      {trackId !== null ? <EndThread trackId={trackId} /> : null}
    </div>
  );
}
