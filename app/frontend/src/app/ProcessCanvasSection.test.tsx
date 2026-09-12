// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { SessionContext } from '../features/auth';
import { ProcessCanvasSection } from './App';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

interface CanvasProps {
  instanceId: string;
  viewerId?: string;
  renderTaskActions?: (subject: {
    taskId: string;
    state: string;
    mine: boolean;
    hasDeadline: boolean;
    deadline: string | null;
    onMoved: () => void;
  }) => React.ReactNode;
  renderAssign?: (subject: {
    instanceId: string;
    stepId: string;
    onClose: () => void;
    onAssigned: () => void;
  }) => React.ReactNode;
}

vi.mock('../features/canvas', () => ({
  ProcessCanvasScreen: (props: CanvasProps) => {
    return (
      <div>
        <p>{`instance:${props.instanceId} viewer:${props.viewerId ?? 'nobody'}`}</p>

        {props.renderTaskActions?.({
          taskId: 't1',
          state: 'IN_PROGRESS',
          mine: false,
          hasDeadline: false,
          deadline: null,
          onMoved: () => undefined,
        })}
        {props.renderAssign?.({
          instanceId: props.instanceId,
          stepId: 's1',
          onClose: () => undefined,
          onAssigned: () => undefined,
        })}
      </div>
    );
  },
}));

vi.mock('../features/task', () => ({
  WorkScreen: () => null,
  TaskActionsPanel: (props: {
    taskId: string;
    permissions: readonly string[];
    mine: boolean;
    directedByMe: boolean;
    hasDeadline: boolean;
    currentDeadline: string | null;
    onMoved?: () => void;
  }) => (
    <p>
      {`task-actions:${props.taskId}:${props.permissions.join('+')}` +
        `:mine=${props.mine}:directed=${props.directedByMe}` +
        `:hasDeadline=${props.hasDeadline}:deadline=${String(props.currentDeadline)}` +
        `:moved=${typeof props.onMoved}`}
    </p>
  ),
}));

vi.mock('../features/process', () => ({
  ProcessScreen: () => null,
  AssignStepFromInstance: (props: { instanceId: string; stepId: string }) => (
    <p>{`assign:${props.instanceId}:${props.stepId}`}</p>
  ),

  useInstances: () => ({ isPending: false, isError: false, data: { instances: [] } }),
}));

vi.mock('../features/workspace', () => ({
  MyDataScreen: () => null,
  PeopleScreen: () => null,
  SettingsScreen: () => null,
  WorkspaceSetupScreen: () => null,
  useActiveColleagues: () => ({ data: undefined }),
}));

vi.mock('../features/auth', () => ({
  AccountPanel: () => null,
  AccountStrip: () => null,
  AuthGate: () => null,
  useSessionContext: () => ({ isPending: false, data: null }),
}));

function session(over: Partial<SessionContext> = {}): SessionContext {
  return {
    userId: 'p1',
    email: 'ioana@atelier.ro',
    permissions: ['TASK_ACT_OWN', 'PROCESS_ASSIGN'],
    landingTarget: 'WORKSPACE',
    serverTime: '2026-08-20T09:00:00Z',
    ...over,
  } as SessionContext;
}

afterEach(cleanup);

describe('where the canvas meets the features that own its actions', () => {
  it('tells the canvas who is looking', () => {
    render(<ProcessCanvasSection session={session()} instanceId="i1" locale="en" />);

    expect(screen.getByText('instance:i1 viewer:p1')).toBeTruthy();
  });

  it('builds the moves from TASK, with what the viewer actually holds', () => {
    render(<ProcessCanvasSection session={session()} instanceId="i1" locale="en" />);

    expect(
      screen.getByText(
        'task-actions:t1:TASK_ACT_OWN+PROCESS_ASSIGN:mine=false:directed=false' +
          ':hasDeadline=false:deadline=null:moved=undefined',
      ),
    ).toBeTruthy();
  });

  it('builds the assignment from PROCESS, on the step the canvas named', () => {
    render(<ProcessCanvasSection session={session()} instanceId="i1" locale="en" />);

    expect(screen.getByText('assign:i1:s1')).toBeTruthy();
  });

  it('claims nobody directed the work, rather than guessing that somebody did', () => {
    render(<ProcessCanvasSection session={session()} instanceId="i1" locale="en" />);

    expect(screen.getByText(/:directed=false:/)).toBeTruthy();
  });
});
