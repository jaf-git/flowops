// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { RunComparison } from '../api/nodePipelineApi';
import { ComparePanel } from './ComparePanel';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

let state: { data?: RunComparison | null } = { data: null };

vi.mock('../hooks/useAnalysis', () => ({
  useRunComparison: () => state,
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  state = { data: null };
});

function comparison(over: Partial<RunComparison> = {}): RunComparison {
  return {
    runId: 'r-1',
    aiMode: 'COMPARE',
    compared: 348,
    agreed: 330,
    raised: 4,
    lowered: 9,
    changed: 3,
    failed: 2,
    modelId: 'llama3.2:3b',
    promptVersion: 'v1',
    ...over,
  };
}

describe('what the model made of a run', () => {
  it('renders nothing at all when the run was never in COMPARE', () => {
    const { container } = render(<ComparePanel runId="r-1" />);

    expect(container.firstChild).toBeNull();
  });

  it('renders nothing before anything has run', () => {
    state = { data: undefined };

    const { container } = render(<ComparePanel runId={undefined} />);

    expect(container.firstChild).toBeNull();
  });

  it('shows five buckets, and changed is one of them', () => {
    state = { data: comparison() };

    render(<ComparePanel runId="r-1" />);

    expect(screen.getByText('pipeline.compare.bucket.agreed')).toBeTruthy();
    expect(screen.getByText('pipeline.compare.bucket.raised')).toBeTruthy();
    expect(screen.getByText('pipeline.compare.bucket.lowered')).toBeTruthy();
    expect(screen.getByText('pipeline.compare.bucket.changed')).toBeTruthy();
    expect(screen.getByText('pipeline.compare.bucket.failed')).toBeTruthy();
  });

  it('states that the cost of comparing is not recorded', () => {
    state = { data: comparison() };

    render(<ComparePanel runId="r-1" />);

    expect(screen.getByText('pipeline.compare.cost')).toBeTruthy();
  });

  it('carries its own end date', () => {
    state = { data: comparison() };

    render(<ComparePanel runId="r-1" />);

    expect(screen.getByText('pipeline.compare.endDate')).toBeTruthy();
  });

  it('names the model that answered', () => {
    state = { data: comparison() };

    render(<ComparePanel runId="r-1" />);

    expect(screen.getByText(/llama3\.2:3b/)).toBeTruthy();
  });

  it('says when no model answered', () => {
    state = { data: comparison({ modelId: null }) };

    render(<ComparePanel runId="r-1" />);

    expect(screen.getByText('pipeline.compare.noModel')).toBeTruthy();
  });
});
