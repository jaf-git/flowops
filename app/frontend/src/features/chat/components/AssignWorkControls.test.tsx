// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import type { AssignmentContext } from '../api/chatApi';
import { AssignWorkControls } from './AssignWorkControls';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}));

afterEach(cleanup);

function context(over: Partial<AssignmentContext> = {}): AssignmentContext {
  return {
    counterpartId: 'p2',
    counterpartName: 'Raluca Ionescu',
    counterpartActive: true,
    mayAssignTask: true,
    mayStartRun: true,
    templates: [{ id: 'tpl1', name: 'Comandă mobilier', overview: null, stepCount: 2 }],
    ...over,
  };
}

describe('the two controls in the conversation header', () => {
  it('offers both where the counterpart may be given either', () => {
    render(
      <AssignWorkControls context={context()} onGiveTask={() => {}} onStartProcess={() => {}} />,
    );

    expect(screen.getByLabelText('chat.assign.task')).toBeTruthy();
    expect(screen.getByLabelText('chat.assign.process')).toBeTruthy();
  });

  it('renders nothing at all until the answer has arrived', () => {
    const { container } = render(
      <AssignWorkControls context={undefined} onGiveTask={() => {}} onStartProcess={() => {}} />,
    );

    expect(container.textContent).toBe('');
  });

  it('renders nothing where there is no one other person', () => {
    const { container } = render(
      <AssignWorkControls
        context={context({
          counterpartId: null,
          counterpartName: null,
          mayAssignTask: false,
          mayStartRun: false,
        })}
        onGiveTask={() => {}}
        onStartProcess={() => {}}
      />,
    );

    expect(container.textContent).toBe('');
  });

  it('renders nothing where the counterpart has gone', () => {
    const { container } = render(
      <AssignWorkControls
        context={context({ counterpartActive: false })}
        onGiveTask={() => {}}
        onStartProcess={() => {}}
      />,
    );

    expect(container.textContent).toBe('');
  });

  it('hides the task control and keeps the process control outside my subtree', () => {
    render(
      <AssignWorkControls
        context={context({ mayAssignTask: false })}
        onGiveTask={() => {}}
        onStartProcess={() => {}}
      />,
    );

    expect(screen.queryByLabelText('chat.assign.task')).toBeNull();
    expect(screen.getByLabelText('chat.assign.process')).toBeTruthy();
  });

  it('keeps the process control when there are no templates yet', () => {
    render(
      <AssignWorkControls
        context={context({ templates: [] })}
        onGiveTask={() => {}}
        onStartProcess={() => {}}
      />,
    );

    expect(screen.getByLabelText('chat.assign.process')).toBeTruthy();
  });

  it('hides the process control from somebody who may not start runs, even with templates present', () => {
    render(
      <AssignWorkControls
        context={context({ mayStartRun: false })}
        onGiveTask={() => {}}
        onStartProcess={() => {}}
      />,
    );

    expect(screen.queryByLabelText('chat.assign.process')).toBeNull();

    expect(screen.getByLabelText('chat.assign.task')).toBeTruthy();
  });

  it('asks the caller to open each dialog rather than opening one itself', () => {
    const gave = vi.fn();
    const started = vi.fn();
    render(<AssignWorkControls context={context()} onGiveTask={gave} onStartProcess={started} />);

    fireEvent.click(screen.getByLabelText('chat.assign.task'));
    fireEvent.click(screen.getByLabelText('chat.assign.process'));

    expect(gave).toHaveBeenCalledOnce();
    expect(started).toHaveBeenCalledOnce();
  });

  it('announces the word and hides the letter', () => {
    render(
      <AssignWorkControls context={context()} onGiveTask={() => {}} onStartProcess={() => {}} />,
    );

    const task = screen.getByLabelText('chat.assign.task');
    expect(task.getAttribute('aria-label')).toBe('chat.assign.task');
    expect(task.querySelector('[aria-hidden="true"]')).toBeTruthy();
  });
});

describe('the copy this feature needs', () => {
  const keys = [
    'task',
    'process',
    'noTemplates',

    'authorOne',
    'steeredBy',
    'chooseTemplate',
    'start',
    'cancel',
  ];

  it.each(keys)('says %s', (key) => {
    expect((en.chat.assign as Record<string, string>)[key]).toBeTruthy();
  });

  it('says what happened in a thread', () => {
    expect(en.chat.workMark.gaveTask).toBeTruthy();
    expect(en.chat.workMark.startedRun).toBeTruthy();
  });
});
