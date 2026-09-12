// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import type { InstanceStepPayload } from '../model/fromInstance';
import { StepInspector } from './StepInspector';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string | number>) => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          en as unknown,
        );

      return typeof phrase === 'string'
        ? phrase.replace(/{{(\w+)}}/g, (_, name: string) => String(values?.[name] ?? ''))
        : key;
    },
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function step(over: Partial<InstanceStepPayload> = {}): InstanceStepPayload {
  return {
    id: 's1',
    title: 'Crearea conturilor IT',
    position: 3,
    condition: 'ASSIGNED',
    taskId: 't1',
    taskState: 'IN_PROGRESS',
    blockedReason: null,
    assigneeId: 'p1',
    assigneeName: 'Dan Stan',
    deadline: '2026-08-20T09:00:00Z',
    atRisk: false,
    phases: [{ kind: 'ACTIVE', seconds: 7200 }],
    ...over,
  };
}

function open(
  over: Partial<InstanceStepPayload> = {},
  props: Partial<Parameters<typeof StepInspector>[0]> = {},
) {
  const onAssign = vi.fn();

  const rendered = render(
    <StepInspector
      step={step(over)}
      onClose={vi.fn()}
      onOpenTask={vi.fn()}
      onAssign={onAssign}
      actions={<button type="button">{en.task.block.action}</button>}
      {...props}
    />,
  );

  return { ...rendered, onAssign };
}

describe('the step inspector', () => {
  it('renders whatever TASK handed it, and never a rail of its own', () => {
    open();

    expect(screen.getByRole('button', { name: en.task.block.action })).toBeTruthy();
  });

  it('renders no action region at all where nothing was handed to it', () => {
    open({ taskState: 'IN_PROGRESS' }, { actions: undefined });

    expect(screen.queryByRole('button', { name: en.task.block.action })).toBeNull();
  });

  it('never offers a comment, anywhere, in this pass', () => {
    const { container } = open();

    expect(container.textContent?.toLowerCase()).not.toContain('comment');
  });

  it('puts the moves inside the step they belong to, rather than beside it', () => {
    open();

    const inspector = screen.getByTestId('step-inspector');

    expect(inspector.getAttribute('aria-label')).toBe('Crearea conturilor IT');
    expect(inspector.contains(screen.getByRole('button', { name: en.task.block.action }))).toBe(
      true,
    );
  });

  it('offers the assign action only on a step that is reachable', () => {
    open({ condition: 'REACHABLE', taskState: null, taskId: null, assigneeId: null });

    expect(screen.getByRole('button', { name: en.canvas.node.assign })).toBeTruthy();
    cleanup();

    open({ condition: 'ASSIGNED' });
    expect(screen.queryByRole('button', { name: en.canvas.node.assign })).toBeNull();
  });

  it('states the phase rule out loud, once, where the numbers are densest', () => {
    open();

    expect(screen.getByText(en.canvas.inspector.phaseRule)).toBeTruthy();
  });

  it('shows a blocked step’s reason in full', () => {
    open({ taskState: 'BLOCKED', blockedReason: 'Se așteaptă reînnoirea licenței de la furnizor' });

    expect(screen.getByTestId('inspector-blocker').textContent).toBe(
      'Se așteaptă reînnoirea licenței de la furnizor',
    );
  });

  it('renders an erased assignee as a former member', () => {
    open({ assigneeName: '' });

    expect(screen.getByText(en.ui.personChip.formerMember)).toBeTruthy();
    expect(screen.queryByText('Dan Stan')).toBeNull();
  });

  it('shows a step nobody can start yet without offering a move on it', () => {
    open(
      { condition: 'PENDING', taskId: null, taskState: null, assigneeId: null, phases: [] },
      { actions: undefined },
    );

    expect(screen.getByText(en.canvas.condition.Pending)).toBeTruthy();
    expect(screen.queryByTestId('inspector-phases')).toBeNull();
    expect(screen.queryByRole('button', { name: en.canvas.node.assign })).toBeNull();
    expect(screen.queryByRole('button', { name: en.task.block.action })).toBeNull();
  });
});
