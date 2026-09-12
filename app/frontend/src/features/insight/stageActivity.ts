import { STAGES, type Stage } from './api/analysisApi';

export function somethingHappenedAt(
  stage: Stage,
  reachedByPipeline: string | undefined,
  findings: number,
): boolean {
  return findings > 0 || pipelineReached(stage, reachedByPipeline);
}

export function pipelineReached(stage: Stage, reachedByPipeline: string | undefined): boolean {
  if (reachedByPipeline === undefined) {
    return false;
  }
  const here = STAGES.indexOf(stage);
  const got = STAGES.indexOf(reachedByPipeline as Stage);
  return got >= 0 && here <= got;
}
