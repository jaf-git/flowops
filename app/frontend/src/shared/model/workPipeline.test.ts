import { describe, expect, it } from 'vitest';

import { birthOf, stagesOf, type WorkPipelineInput } from './workPipeline';

function work(over: Partial<WorkPipelineInput> = {}): WorkPipelineInput {
  return {
    taskId: 'task-1',
    taskTitle: 'Pregătește raportul lunar',
    taskState: 'DONE',
    templateId: null,
    templateTitle: null,
    templateIsDraft: false,
    origin: null,
    analysis: null,
    lineage: null,
    observations: [],
    ...over,
  };
}

describe('the work pipeline', () => {
  it('draws only the stages that exist', () => {
    expect(stagesOf(work()).map((stage) => stage.kind)).toEqual(['TASK']);
  });

  it('keeps the stages in the order the story runs', () => {
    const stages = stagesOf(
      work({
        origin: { conversationId: 'c-1', messageId: 'm-1' },
        templateId: 't-1',
        templateTitle: 'Raport lunar',
        analysis: { stamped: 9, medianActiveSeconds: 9600, passedFirstTime: 7, reviewed: 9 },
        lineage: {
          processTemplates: [
            { templateId: 'p-1', name: 'Calendar lunar', position: 2, active: true },
          ],
          runs: [],
          runsTotal: 12,
        },
        observations: [{ kind: 'MISSING_STEP', findingKey: 'k-1' }],
      }),
    ).map((stage) => stage.kind);

    expect(stages).toEqual([
      'MESSAGE',
      'TASK',
      'TEMPLATE',
      'ANALYSIS',
      'PLANNED_IN',
      'OBSERVATIONS',
    ]);
  });

  it('withholds the analysis stage until the work has been done more than once', () => {
    const once = work({
      templateId: 't-1',
      analysis: { stamped: 1, medianActiveSeconds: 3600, passedFirstTime: 1, reviewed: 1 },
    });

    expect(stagesOf(once).map((stage) => stage.kind)).not.toContain('ANALYSIS');

    const twice = work({
      templateId: 't-1',
      analysis: { stamped: 2, medianActiveSeconds: 3600, passedFirstTime: 2, reviewed: 2 },
    });

    expect(stagesOf(twice).map((stage) => stage.kind)).toContain('ANALYSIS');
  });

  describe('how the work was born', () => {
    it('is from a message when there is one', () => {
      expect(birthOf(work({ origin: { conversationId: 'c', messageId: 'm' } }))).toBe(
        'FROM_MESSAGE',
      );
    });

    it('is stamped when it names an approved entry', () => {
      expect(birthOf(work({ templateId: 't-1', templateIsDraft: false }))).toBe('STAMPED');
    });

    it('is typed by hand when the entry it names is the draft it produced', () => {
      expect(birthOf(work({ templateId: 't-1', templateIsDraft: true }))).toBe('TYPED');
    });
  });
});
