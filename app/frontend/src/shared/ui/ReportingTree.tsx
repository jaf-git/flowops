import { useRef, useState, type JSX, type KeyboardEvent, type ReactNode } from 'react';

export interface ReportingTreeNode {
  id: string;

  content: ReactNode;
  children: ReportingTreeNode[];
}

interface ReportingTreeProps {
  label: string;
  nodes: readonly ReportingTreeNode[];
}

export function ReportingTree({ label, nodes }: ReportingTreeProps): JSX.Element {
  const container = useRef<HTMLUListElement>(null);
  const flattened = flatten(nodes);
  const [focused, setFocused] = useState<string | undefined>(flattened[0]?.id);

  const order = flattened.map((node) => node.id);
  const active = focused !== undefined && order.includes(focused) ? focused : order[0];

  function move(event: KeyboardEvent<HTMLUListElement>): void {
    if (
      event.target !== event.currentTarget &&
      !(event.target as HTMLElement).matches('[role="treeitem"]')
    ) {
      return;
    }

    const current = order.indexOf(active ?? '');
    const next = destination(event.key, current, order.length);
    if (next === undefined) {
      return;
    }

    event.preventDefault();
    const id = order[next];
    if (id === undefined) {
      return;
    }
    setFocused(id);
    container.current?.querySelector<HTMLElement>(`[data-tree-item="${cssEscape(id)}"]`)?.focus();
  }

  return (
    <ul ref={container} role="tree" aria-label={label} onKeyDown={move} className="ui-tree">
      {nodes.map((node) => (
        <Branch key={node.id} node={node} level={1} active={active} />
      ))}
    </ul>
  );
}

function Branch({
  node,
  level,
  active,
}: {
  node: ReportingTreeNode;
  level: number;
  active: string | undefined;
}): JSX.Element {
  const hasChildren = node.children.length > 0;

  return (
    <li
      role="treeitem"
      aria-level={level}
      aria-expanded={hasChildren ? true : undefined}
      className="ui-tree-item"
      data-tree-item={node.id}
      tabIndex={node.id === active ? 0 : -1}
    >
      {node.content}

      {hasChildren ? (
        <ul role="group" className="ui-tree-group">
          {node.children.map((child) => (
            <Branch key={child.id} node={child} level={level + 1} active={active} />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

function destination(key: string, current: number, count: number): number | undefined {
  switch (key) {
    case 'ArrowDown':
      return Math.min(current + 1, count - 1);
    case 'ArrowUp':
      return Math.max(current - 1, 0);
    case 'Home':
      return 0;
    case 'End':
      return count - 1;
    default:
      return undefined;
  }
}

function flatten(nodes: readonly ReportingTreeNode[]): ReportingTreeNode[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children)]);
}

function cssEscape(value: string): string {
  return value.replace(/["\\]/g, '\\$&');
}
