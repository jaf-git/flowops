// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { DependencyList, type DependencyEdge } from './DependencyList';

const STEPS = [
  { id: 'a', title: 'Pregătește echipamentul' },
  { id: 'b', title: 'Prima zi' },
  { id: 'c', title: 'Evaluare la o lună' },
];

const LABELS = {
  waitsFor: 'Așteaptă după',
  waitsForNothing: 'Poate începe imediat',
  add: 'Adaugă ceva după care așteaptă',
  remove: 'Nu mai așteaptă după acesta',
};

function draw(
  edges: DependencyEdge[],
  overrides: Partial<Parameters<typeof DependencyList>[0]> = {},
) {
  const onAdd = vi.fn();
  const onRemove = vi.fn();
  render(
    <DependencyList
      steps={STEPS}
      edges={edges}
      labels={LABELS}
      onAdd={onAdd}
      onRemove={onRemove}
      {...overrides}
    />,
  );
  return { onAdd, onRemove };
}

function addControlFor(title: string): HTMLElement {
  return screen.getByLabelText(`${LABELS.add}: ${title}`);
}

function optionsOf(control: HTMLElement): string[] {
  return Array.from(control.querySelectorAll('option'))
    .map((option) => option.textContent ?? '')
    .filter((text) => text !== '');
}

afterEach(() => {
  cleanup();
});

describe('the dependency list', () => {
  it('says plainly when a step waits for nothing', () => {
    draw([]);

    expect(screen.queryAllByText(LABELS.waitsForNothing)).toHaveLength(3);
  });

  it('names what a step waits for', () => {
    draw([{ dependentStepId: 'b', dependsOnStepId: 'a' }]);

    expect(screen.queryByLabelText(`${LABELS.waitsFor}: Prima zi`)).not.toBeNull();
  });

  it('does not offer a choice that would close a cycle', () => {
    draw([{ dependentStepId: 'b', dependsOnStepId: 'a' }]);

    const offered = optionsOf(addControlFor('Pregătește echipamentul'));

    expect(offered).not.toContain('Prima zi');
    expect(offered).toContain('Evaluare la o lună');
  });

  it('offers no control at all to a step with nothing it could legally wait for', () => {
    draw([
      { dependentStepId: 'b', dependsOnStepId: 'a' },
      { dependentStepId: 'c', dependsOnStepId: 'b' },
    ]);

    expect(screen.queryByLabelText(`${LABELS.add}: Pregătește echipamentul`)).toBeNull();
    expect(screen.queryByLabelText(`${LABELS.add}: Prima zi`)).toBeNull();
    expect(optionsOf(addControlFor('Evaluare la o lună'))).toEqual(['Pregătește echipamentul']);
  });

  it('does not offer an edge that is already drawn', () => {
    draw([{ dependentStepId: 'b', dependsOnStepId: 'a' }]);

    const offered = optionsOf(addControlFor('Prima zi'));

    expect(offered).not.toContain('Pregătește echipamentul');
    expect(offered).toContain('Evaluare la o lună');
  });

  it('draws an edge when a choice is made', async () => {
    const { onAdd } = draw([]);

    await userEvent.selectOptions(addControlFor('Pregătește echipamentul'), 'b');

    expect(onAdd).toHaveBeenCalledWith({ dependentStepId: 'a', dependsOnStepId: 'b' });
  });

  it('takes an edge back out', async () => {
    const { onRemove } = draw([{ dependentStepId: 'b', dependsOnStepId: 'a' }]);

    await userEvent.click(screen.getByText(LABELS.remove));

    expect(onRemove).toHaveBeenCalledWith({ dependentStepId: 'b', dependsOnStepId: 'a' });
  });

  it('marks the steps the server said were in a cycle', () => {
    draw([{ dependentStepId: 'b', dependsOnStepId: 'a' }], { inCycle: ['a', 'b'] });

    expect(document.querySelectorAll('[data-in-cycle="true"]')).toHaveLength(2);
  });

  it('offers nothing while a change is in flight', () => {
    draw([{ dependentStepId: 'b', dependsOnStepId: 'a' }], { disabled: true });

    expect(screen.getByText(LABELS.remove)).toHaveProperty('disabled', true);
    expect(addControlFor('Pregătește echipamentul')).toHaveProperty('disabled', true);
  });
});
