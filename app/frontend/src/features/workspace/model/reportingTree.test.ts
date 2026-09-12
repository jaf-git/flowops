import { describe, expect, it } from 'vitest';

import {
  buildReportingTree,
  isHiddenByDefault,
  managerOptions,
  reassignmentTargets,
  reportingPathToward,
  type PendingInvitation,
  type Person,
} from './reportingTree';

function person(overrides: Partial<Person> & Pick<Person, 'membershipId' | 'displayName'>): Person {
  return {
    personId: `person-${overrides.membershipId}`,
    role: 'EMPLOYEE',
    managerId: null,
    status: 'ACTIVE',
    deactivatedAt: null,
    isSelf: false,
    ...overrides,
  };
}

function invitation(
  overrides: Partial<PendingInvitation> & Pick<PendingInvitation, 'id'>,
): PendingInvitation {
  return {
    emailAddress: 'stefan@atelier.ro',
    intendedRole: 'EMPLOYEE',
    intendedManagerId: 'maria',
    inviterId: 'maria',
    state: 'SENT',
    expiresAt: '2026-08-11T09:00:00Z',
    ...overrides,
  };
}

const MARIA = person({
  membershipId: 'maria',
  displayName: 'Maria Ionescu',
  role: 'OWNER',
  isSelf: true,
});
const IONUT = person({
  membershipId: 'ionut',
  displayName: 'Ionuț Petrescu',
  role: 'MANAGER',
  managerId: 'maria',
});
const IOANA = person({ membershipId: 'ioana', displayName: 'Ioana Radu', managerId: 'ionut' });
const ANDREI = person({
  membershipId: 'andrei',
  displayName: 'Andrei Munteanu',
  managerId: 'maria',
  status: 'DEACTIVATED',
});

const CATALIN = person({
  membershipId: 'catalin',
  displayName: 'Cătălin Stoica',
  role: 'MANAGER',
  managerId: 'maria',
  status: 'DEACTIVATED',
});

describe('the reporting tree', () => {
  it('roots itself at the person with no manager', () => {
    const tree = buildReportingTree([IONUT, IOANA, MARIA]);

    expect(tree).toHaveLength(1);
    expect(tree[0]).toMatchObject({ kind: 'person', person: { displayName: 'Maria Ionescu' } });
  });

  it('nests each person under the manager they report to, however deep', () => {
    const tree = buildReportingTree([MARIA, IONUT, IOANA]);

    const ionut = tree[0]?.children[0];
    expect(ionut).toMatchObject({ kind: 'person', person: { displayName: 'Ionuț Petrescu' } });
    expect(ionut?.children[0]).toMatchObject({
      kind: 'person',
      person: { displayName: 'Ioana Radu' },
    });
  });

  it('places a pending invitation under its intended manager', () => {
    const tree = buildReportingTree(
      [MARIA, IONUT],
      [invitation({ id: 'inv-1', intendedManagerId: 'ionut' })],
    );

    const ionut = tree[0]?.children[0];
    expect(ionut?.children[0]).toMatchObject({ kind: 'invitation', invitation: { id: 'inv-1' } });
  });

  it('shows a person whose manager is missing rather than losing them', () => {
    const orphan = person({
      membershipId: 'stray',
      displayName: 'Elena Dobre',
      managerId: 'nobody',
    });

    const tree = buildReportingTree([MARIA, orphan]);

    expect(tree.map((node) => (node.kind === 'person' ? node.person.displayName : ''))).toContain(
      'Elena Dobre',
    );
  });

  it('offers only active people who hold a role that permits reports', () => {
    expect(
      managerOptions([MARIA, IONUT, IOANA, ANDREI]).map((option) => option.displayName),
    ).toEqual(['Maria Ionescu', 'Ionuț Petrescu']);
  });

  it('offers only active people who hold a role that permits reports as a new manager', () => {
    expect(
      reassignmentTargets([MARIA, IONUT, IOANA, ANDREI, CATALIN], IONUT).map(
        (target) => target.displayName,
      ),
    ).toEqual(['Maria Ionescu']);
  });

  it('never offers a person themselves as their own new manager', () => {
    expect(reassignmentTargets([MARIA, IONUT], IONUT).map((target) => target.displayName)).toEqual([
      'Maria Ionescu',
    ]);
  });

  it('shows both people in a cycle rather than losing them off the tree', () => {
    const andrei = person({
      membershipId: 'andrei',
      displayName: 'Andrei Munteanu',
      managerId: 'elena',
    });
    const elena = person({
      membershipId: 'elena',
      displayName: 'Elena Dobre',
      managerId: 'andrei',
    });

    const rendered = JSON.stringify(buildReportingTree([MARIA, andrei, elena]));

    expect(rendered).toContain('Andrei Munteanu');
    expect(rendered).toContain('Elena Dobre');
  });

  it('hides a deactivated person by default and nobody else', () => {
    expect(isHiddenByDefault(ANDREI)).toBe(true);
    expect(isHiddenByDefault(IONUT)).toBe(false);
  });

  it('names the path from the person down to a manager who sits below them', () => {
    expect(reportingPathToward([MARIA, IONUT, IOANA], IONUT, 'ioana')).toEqual(['ionut', 'ioana']);
  });

  it('follows the line all the way down, not only the first step', () => {
    const stefan = person({
      membershipId: 'stefan',
      displayName: 'Ștefan Ilie',
      managerId: 'ioana',
    });

    expect(reportingPathToward([MARIA, IONUT, IOANA, stefan], IONUT, 'stefan')).toEqual([
      'ionut',
      'ioana',
      'stefan',
    ]);
  });

  it('finds no loop when the proposed manager does not sit below the person', () => {
    expect(reportingPathToward([MARIA, IONUT, IOANA], IONUT, 'maria')).toBeNull();
  });

  it('finds no loop when somebody is proposed as their own manager', () => {
    expect(reportingPathToward([MARIA, IONUT, IOANA], IONUT, 'ionut')).toBeNull();
  });

  it('gives up rather than looping forever on a tree that already loops', () => {
    const a = person({ membershipId: 'a', displayName: 'A', managerId: 'b' });
    const b = person({ membershipId: 'b', displayName: 'B', managerId: 'a' });

    expect(reportingPathToward([a, b], MARIA, 'a')).toBeNull();
  });
});
