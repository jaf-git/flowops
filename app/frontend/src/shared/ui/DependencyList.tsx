import { type JSX } from 'react';

export interface DependencyNode {
  id: string;
  title: string;
}

export interface DependencyEdge {
  dependentStepId: string;
  dependsOnStepId: string;
}

export interface DependencyListLabels {
  waitsFor: string;
  waitsForNothing: string;
  add: string;
  remove: string;
}

interface DependencyListProps {
  steps: DependencyNode[];
  edges: DependencyEdge[];
  labels: DependencyListLabels;

  inCycle?: readonly string[];
  onAdd: (edge: DependencyEdge) => void;
  onRemove: (edge: DependencyEdge) => void;
  disabled?: boolean;
}

export function DependencyList({
  steps,
  edges,
  labels,
  inCycle = [],
  onAdd,
  onRemove,
  disabled = false,
}: DependencyListProps): JSX.Element {
  return (
    <ul className="ui-dep">
      {steps.map((step) => {
        const waitsFor = edges
          .filter((edge) => edge.dependentStepId === step.id)
          .map((edge) => steps.find((each) => each.id === edge.dependsOnStepId))
          .filter((each): each is DependencyNode => each !== undefined);

        return (
          <li
            key={step.id}

            data-in-cycle={inCycle.includes(step.id) ? 'true' : undefined}
            className="ui-dep-row"
          >
            <span className="ui-dep-title">{step.title}</span>

            {waitsFor.length === 0 ? (
              <span className="ui-dep-none">{labels.waitsForNothing}</span>
            ) : (
              <ul aria-label={`${labels.waitsFor}: ${step.title}`} className="ui-dep-waits">
                {waitsFor.map((other) => (
                  <li key={other.id} className="ui-dep-waits-item">
                    <span className="ui-dep-waits-name">{other.title}</span>
                    <button
                      type="button"
                      className="ui-dep-remove"
                      disabled={disabled}
                      onClick={() => {
                        onRemove({ dependentStepId: step.id, dependsOnStepId: other.id });
                      }}
                    >
                      {labels.remove}
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <AddDependency
              step={step}
              steps={steps}
              edges={edges}
              label={labels.add}
              disabled={disabled}
              onAdd={onAdd}
            />
          </li>
        );
      })}
    </ul>
  );
}

function AddDependency({
  step,
  steps,
  edges,
  label,
  disabled,
  onAdd,
}: {
  step: DependencyNode;
  steps: DependencyNode[];
  edges: DependencyEdge[];
  label: string;
  disabled: boolean;
  onAdd: (edge: DependencyEdge) => void;
}): JSX.Element | null {
  const choices = steps.filter(
    (other) => other.id !== step.id && wouldBeAccepted(step, other, edges),
  );

  if (choices.length === 0) {
    return null;
  }

  return (
    <label className="ui-dep-add">
      {label}
      <select
        aria-label={`${label}: ${step.title}`}
        disabled={disabled}
        value=""
        onChange={(event) => {
          if (event.target.value !== '') {
            onAdd({ dependentStepId: step.id, dependsOnStepId: event.target.value });
          }
        }}
      >
        <option value="" />
        {choices.map((other) => (
          <option key={other.id} value={other.id}>
            {other.title}
          </option>
        ))}
      </select>
    </label>
  );
}

function wouldBeAccepted(
  step: DependencyNode,
  other: DependencyNode,
  edges: DependencyEdge[],
): boolean {
  const alreadyDrawn = edges.some(
    (edge) => edge.dependentStepId === step.id && edge.dependsOnStepId === other.id,
  );
  if (alreadyDrawn) {
    return false;
  }
  return !waitsFor(other.id, step.id, edges, new Set());
}

function waitsFor(
  from: string,
  target: string,
  edges: DependencyEdge[],
  seen: Set<string>,
): boolean {
  if (from === target) {
    return true;
  }
  if (seen.has(from)) {
    return false;
  }
  seen.add(from);
  return edges
    .filter((edge) => edge.dependentStepId === from)
    .some((edge) => waitsFor(edge.dependsOnStepId, target, edges, seen));
}
