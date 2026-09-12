export type MembershipStatus = 'ACTIVE' | 'DEACTIVATED';

export type InvitedRole = 'MANAGER' | 'EMPLOYEE';

export interface Person {
  membershipId: string;
  personId: string;
  displayName: string;
  role: string;

  managerId: string | null;
  status: MembershipStatus;

  deactivatedAt: string | null;
  isSelf: boolean;
}

export interface PendingInvitation {
  id: string;
  emailAddress: string;
  intendedRole: InvitedRole;
  intendedManagerId: string;
  inviterId: string;
  state: string;
  expiresAt: string;
}

export type TreeNode =
  | { kind: 'person'; person: Person; children: TreeNode[] }
  | { kind: 'invitation'; invitation: PendingInvitation; children: TreeNode[] };

export function buildReportingTree(
  people: readonly Person[],
  invitations: readonly PendingInvitation[] = [],
): TreeNode[] {
  const nodes = new Map<string, TreeNode>();
  for (const person of people) {
    nodes.set(person.membershipId, { kind: 'person', person, children: [] });
  }

  const roots: TreeNode[] = [];
  for (const person of people) {
    const node = nodes.get(person.membershipId);
    if (node === undefined) {
      continue;
    }
    const parent = person.managerId === null ? undefined : nodes.get(person.managerId);
    if (parent === undefined) {
      roots.push(node);
    } else {
      parent.children.push(node);
    }
  }

  const reachable = new Set<string>();
  const walk = (node: TreeNode): void => {
    const id = node.kind === 'person' ? node.person.membershipId : node.invitation.id;
    if (reachable.has(id)) {
      return;
    }
    reachable.add(id);
    node.children.forEach(walk);
  };
  roots.forEach(walk);

  for (const person of people) {
    if (!reachable.has(person.membershipId)) {
      const node = nodes.get(person.membershipId);
      if (node !== undefined) {
        node.children = node.children.filter(
          (child) => child.kind !== 'person' || !nodes.has(child.person.membershipId),
        );
        roots.push(node);
        walk(node);
      }
    }
  }

  for (const invitation of invitations) {
    const node: TreeNode = { kind: 'invitation', invitation, children: [] };
    const parent = nodes.get(invitation.intendedManagerId);
    if (parent === undefined) {
      roots.push(node);
    } else {
      parent.children.push(node);
    }
  }

  return roots;
}

export function managerOptions(people: readonly Person[]): Person[] {
  return people.filter((person) => person.status === 'ACTIVE' && person.role !== 'EMPLOYEE');
}

export function isHiddenByDefault(person: Person): boolean {
  return person.status === 'DEACTIVATED';
}

export function reassignmentTargets(people: readonly Person[], person: Person): Person[] {
  return people.filter(
    (candidate) =>
      candidate.status === 'ACTIVE' &&
      candidate.role !== 'EMPLOYEE' &&
      candidate.membershipId !== person.membershipId,
  );
}

export function reportingPathToward(
  people: readonly Person[],
  person: Person,
  proposedManagerId: string,
): string[] | null {
  if (proposedManagerId === person.membershipId) {
    return null;
  }

  const managerOf = new Map(
    people.map((candidate) => [candidate.membershipId, candidate.managerId]),
  );
  const path: string[] = [];
  let step: string | null = proposedManagerId;

  for (let guard = 0; guard <= people.length && step !== null; guard += 1) {
    path.push(step);
    if (step === person.membershipId) {
      return path.reverse();
    }
    step = managerOf.get(step) ?? null;
  }
  return null;
}
