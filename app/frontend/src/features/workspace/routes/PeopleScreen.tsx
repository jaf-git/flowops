import { useMemo, useState, type JSX } from 'react';
import { Trans, useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Card } from '../../../shared/ui/Card';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { Chip } from '../../../shared/ui/Chip';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { PersonChip } from '../../../shared/ui/PersonChip';
import { RelativeTime } from '../../../shared/ui/RelativeTime';
import { ReportingTree, type ReportingTreeNode } from '../../../shared/ui/ReportingTree';
import { useAnnounce } from '../../../shared/notice/useNotices';
import { Spinner } from '../../../shared/ui/Spinner';
import type { PendingInvitation, Person, RevokedInvitation } from '../api/workspaceApi';
import { InvitePersonDialog } from '../components/InvitePersonDialog';
import { MoveReportingLineDialog } from '../components/MoveReportingLineDialog';
import { DeactivatePersonDialog } from '../components/DeactivatePersonDialog';
import { ErasePersonDialog } from '../components/ErasePersonDialog';
import { RevokeInvitationDialog } from '../components/RevokeInvitationDialog';
import { usePeople } from '../hooks/usePeople';
import {
  buildReportingTree,
  isHiddenByDefault,
  managerOptions,
  type TreeNode,
} from '../model/reportingTree';

export function PeopleScreen(): JSX.Element {
  const { t } = useTranslation();
  const people = usePeople();
  const [inviting, setInviting] = useState(false);
  const [showDeactivated, setShowDeactivated] = useState(false);
  const [revoking, setRevoking] = useState<PendingInvitation | undefined>(undefined);
  const [moving, setMoving] = useState<Person | undefined>(undefined);
  const [deactivating, setDeactivating] = useState<Person | undefined>(undefined);
  const [erasing, setErasing] = useState<Person | undefined>(undefined);

  const announce = useAnnounce();

  const setDeactivatedNotice = (name: string): void =>
    announce({ tone: 'done', message: t('workspace.deactivate.done', { name }) });

  const setErasedNotice = (name: string): void =>
    announce({ tone: 'done', message: t('workspace.erase.done', { name }) });

  const setWithdrawn = (invitation: RevokedInvitation): void =>
    announce({
      tone: 'done',
      message:
        invitation.revokedAt === null
          ? t('workspace.revoke.alreadyGone', { address: invitation.emailAddress })
          : t('workspace.revoke.withdrawn', { address: invitation.emailAddress }),
    });

  const visible = useMemo(
    () =>
      (people.data?.people ?? []).filter((person) => showDeactivated || !isHiddenByDefault(person)),
    [people.data, showDeactivated],
  );

  const tree = useMemo(
    () => buildReportingTree(visible, people.data?.invitations ?? []),
    [visible, people.data],
  );

  const mayRevoke = people.data?.invitations !== undefined;

  const mayMove = (people.data?.people ?? []).some(
    (person) => person.isSelf && person.role === 'OWNER',
  );

  const mayDeactivate = (people.data?.people ?? []).some(
    (person) => person.isSelf && person.role === 'OWNER',
  );

  const mayErase = (people.data?.people ?? []).some(
    (person) => person.isSelf && person.role === 'OWNER',
  );

  const managers = useMemo(() => managerOptions(people.data?.people ?? []), [people.data]);
  const mayInvite = people.data?.invitations !== undefined;
  const hasDeactivated = (people.data?.people ?? []).some(isHiddenByDefault);

  const nothingToDraw =
    people.data?.onlyMember === true &&
    (people.data.invitations ?? []).length === 0 &&
    !hasDeactivated;

  if (people.isPending) {
    return (
      <Card>
        <Spinner label={t('workspace.people.loading')} />
      </Card>
    );
  }

  if (people.isError) {
    return (
      <Card>
        <EmptyState
          icon="alert"
          heading={t('workspace.people.error.heading')}
          body={t('workspace.people.error.body')}
        />
      </Card>
    );
  }

  return (
    <div style={{ display: 'grid', gap: 'var(--space-5)', maxWidth: '760px' }}>
      <header
        style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)', flexWrap: 'wrap' }}
      >
        <h2
          style={{
            margin: 0,
            fontSize: 'var(--text-lg)',
            fontWeight: 600,
            letterSpacing: '-0.01em',
          }}
        >
          {t('shell.nav.people')}
        </h2>
        {mayInvite && !nothingToDraw ? (
          <Button style={{ marginLeft: 'auto' }} onClick={() => setInviting(true)}>
            {t('workspace.people.invite')}
          </Button>
        ) : null}
      </header>

      {nothingToDraw ? (
        <Card>
          <EmptyState
            icon="person"
            heading={t('workspace.people.alone.heading')}
            body={t('workspace.people.alone.body')}
            action={
              mayInvite ? (
                <Button onClick={() => setInviting(true)}>{t('workspace.people.invite')}</Button>
              ) : undefined
            }
          />
        </Card>
      ) : (
        <Card>
          <ReportingTree
            label={t('workspace.people.treeLabel')}
            nodes={tree.map((node) =>
              toTreeNode(
                node,
                mayRevoke ? setRevoking : undefined,
                mayMove ? setMoving : undefined,
                mayDeactivate ? setDeactivating : undefined,
                mayErase ? setErasing : undefined,
              ),
            )}
          />
        </Card>
      )}

      {hasDeactivated ? (
        <Checkbox
          id="people-show-deactivated"
          checked={showDeactivated}
          onChange={setShowDeactivated}
          label={t('workspace.people.showDeactivated')}
        />
      ) : null}

      {mayInvite ? (
        <InvitePersonDialog
          open={inviting}
          onClose={() => setInviting(false)}
          managers={managers}
        />
      ) : null}

      <DeactivatePersonDialog
        person={deactivating}
        people={people.data?.people ?? []}
        onClose={() => setDeactivating(undefined)}
        onDeactivated={setDeactivatedNotice}
      />

      <ErasePersonDialog
        person={erasing}
        onClose={() => setErasing(undefined)}
        onErased={setErasedNotice}
      />

      <MoveReportingLineDialog
        person={moving}
        people={people.data?.people ?? []}
        onClose={() => setMoving(undefined)}
        onMoved={(personName, newManagerName) =>
          announce({
            tone: 'done',
            message: t('workspace.move.moved', {
              person: personName,
              manager: newManagerName,
            }),
          })
        }
      />

      <RevokeInvitationDialog
        invitation={revoking}
        onClose={() => setRevoking(undefined)}
        onRevoked={setWithdrawn}
      />
    </div>
  );
}

function toTreeNode(
  node: TreeNode,
  onRevoke: ((invitation: PendingInvitation) => void) | undefined,
  onMove: ((person: Person) => void) | undefined,
  onDeactivate: ((person: Person) => void) | undefined,
  onErase: ((person: Person) => void) | undefined,
): ReportingTreeNode {
  return node.kind === 'person'
    ? {
        id: node.person.membershipId,
        content: (
          <PersonRow
            person={node.person}
            onMove={onMove}
            onDeactivate={onDeactivate}
            onErase={onErase}
          />
        ),
        children: node.children.map((child) =>
          toTreeNode(child, onRevoke, onMove, onDeactivate, onErase),
        ),
      }
    : {
        id: node.invitation.id,
        content: <InvitationRow invitation={node.invitation} onRevoke={onRevoke} />,
        children: node.children.map((child) =>
          toTreeNode(child, onRevoke, onMove, onDeactivate, onErase),
        ),
      };
}

function PersonRow({
  person,
  onMove,
  onDeactivate,
  onErase,
}: {
  person: Person;
  onMove: ((person: Person) => void) | undefined;
  onDeactivate: ((person: Person) => void) | undefined;
  onErase: ((person: Person) => void) | undefined;
}): JSX.Element {
  const { t, i18n } = useTranslation();

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
      <PersonChip name={person.displayName} role={t(`workspace.role.${person.role}`)} />

      {person.isSelf ? <Chip tone="brand">{t('workspace.people.you')}</Chip> : null}

      {onMove === undefined || person.managerId === null || person.status !== 'ACTIVE' ? null : (
        <Button
          variant="quiet"
          aria-label={t('workspace.move.actionFor', { name: person.displayName })}
          onClick={() => onMove(person)}
        >
          {t('workspace.move.action')}
        </Button>
      )}

      {onDeactivate === undefined ||
      person.managerId === null ||
      person.status !== 'ACTIVE' ? null : (
        <Button
          variant="quiet"
          aria-label={t('workspace.deactivate.actionFor', { name: person.displayName })}
          onClick={() => onDeactivate(person)}
        >
          {t('workspace.deactivate.action')}
        </Button>
      )}

      {onErase === undefined || person.status !== 'DEACTIVATED' ? null : (
        <Button
          variant="quiet"
          aria-label={t('workspace.erase.actionFor', { name: person.displayName })}
          onClick={() => onErase(person)}
        >
          {t('workspace.erase.action')}
        </Button>
      )}
      {person.status === 'DEACTIVATED' ? (
        <Chip tone="neutral">
          {person.deactivatedAt === null
            ? t('workspace.people.deactivated')
            : t('workspace.people.deactivatedOn', {
                when: dateFormatter(i18n.language).format(new Date(person.deactivatedAt)),
              })}
        </Chip>
      ) : null}
    </div>
  );
}

function InvitationRow({
  invitation,
  onRevoke,
}: {
  invitation: PendingInvitation;
  onRevoke: ((invitation: PendingInvitation) => void) | undefined;
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--space-3)',
        flexWrap: 'wrap',
        color: 'var(--muted)',
      }}
    >
      <span style={{ fontSize: 'var(--text-sm)' }}>{invitation.emailAddress}</span>
      <Chip tone="waiting" dot>
        {t(`workspace.people.invitationState.${invitation.state}`, {
          defaultValue: t('workspace.people.notYetJoined'),
        })}
      </Chip>
      <span style={{ fontSize: 'var(--text-sm)' }}>
        {t(`workspace.role.${invitation.intendedRole}`)}
      </span>
      <span style={{ fontSize: 'var(--text-sm)' }}>
        <Trans
          i18nKey="workspace.people.expires"
          components={{ when: <RelativeTime value={invitation.expiresAt} /> }}
        />
      </span>

      {onRevoke === undefined ? null : (
        <Button
          variant="quiet"

          aria-label={t('workspace.revoke.actionFor', { address: invitation.emailAddress })}
          onClick={() => onRevoke(invitation)}
        >
          {t('workspace.revoke.action')}
        </Button>
      )}
    </div>
  );
}

function dateFormatter(locale: string): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'long', year: 'numeric' });
}
