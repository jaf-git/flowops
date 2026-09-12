import { useEffect, useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { AgreedWork, WorkDraft } from '../../../shared/model/work-draft';
import { Avatar } from '../../../shared/ui/Avatar';
import { Icon } from '../../../shared/ui/Icon';
import { AssignWorkControls } from '../components/AssignWorkControls';
import { ConversationRail, RoomGlyph } from '../components/ConversationRail';
import { MessageThread } from '../components/MessageThread';
import { SelectionBar } from '../components/SelectionBar';
import { StartRunDialog } from '../components/StartRunDialog';
import { barIsOffered, toggled } from '../model/selection';
import {
  useAssignmentContext,
  useAssignTask,
  useBuildProcessFromMessages,
  useCreateGroup,
  useProcessDraftFromMessages,
  useConversations,
  useConversionContext,
  useConvertMessage,
  useMarkRead,
  useMessages,
  useRoomMembership,
  useSendMessage,
  useStartConversation,
} from '../hooks/useChat';

export interface ChatPerson {
  id: string;
  displayName: string;
}

export interface ConversionDialogProps {
  open: boolean;
  prefill: { title: string; description: string; assigneeId: string | undefined };

  processes: readonly { id: string; name: string }[];

  assigneeLocked?: boolean;
  onClose: () => void;

  onConvert: (
    task: {
      title: string;
      description: string | null;
      assigneeId: string;
      deadline: string | null;
      priority: string;

      instanceId: string | null;
    },
    outcome: { onSuccess: () => void; onError: (failure: unknown) => void },
  ) => void;
  converting: boolean;
}

interface ChatRailProps {
  people: readonly ChatPerson[];

  viewerId: string;
  selected: string | undefined;
  onSelect: (id: string) => void;
}

export function ChatRail({ people, viewerId, selected, onSelect }: ChatRailProps): JSX.Element {
  const { t } = useTranslation();
  const conversations = useConversations();
  const start = useStartConversation();
  const createGroup = useCreateGroup();
  const membership = useRoomMembership();
  const [search, setSearch] = useState('');

  const [composing, setComposing] = useState<'DIRECT' | 'GROUP' | undefined>(undefined);
  const [groupName, setGroupName] = useState('');

  const rows = conversations.data?.conversations ?? [];
  const joinable = conversations.data?.joinable ?? [];

  const needle = search.trim().toLowerCase();
  const matches = (haystack: string | null): boolean =>
    (haystack ?? '').toLowerCase().includes(needle);
  const filtered =
    needle === ''
      ? rows
      : rows.filter(
          (row) =>
            matches(row.counterpartName) || matches(row.name) || matches(row.lastMessagePreview),
        );
  const filteredJoinable = needle === '' ? joinable : joinable.filter((room) => matches(room.name));

  const openGroup = (): void => {
    const name = groupName.trim();
    if (name === '') {
      return;
    }
    createGroup.mutate(name, {
      onSuccess: (row) => {
        setGroupName('');
        setComposing(undefined);
        onSelect(row.id);
      },
    });
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          gap: 'var(--space-2)',
          padding: '0 var(--space-3) var(--space-3)',
        }}
      >
        <input
          className="ui-control"
          type="search"
          aria-label={t('chat.rail.searchLabel')}
          placeholder={t('chat.rail.search')}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          style={{ flex: 1, minWidth: 0 }}
        />

        <button
          type="button"
          className="ui-button ui-button-quiet"
          aria-expanded={composing !== undefined}
          onClick={() => setComposing(composing === undefined ? 'DIRECT' : undefined)}
        >
          {t('chat.rail.new')}
        </button>
      </div>

      {composing !== undefined ? (
        <div style={{ padding: '0 var(--space-3) var(--space-4)' }}>
          <div
            role="group"
            aria-label={t('chat.rail.newLabel')}
            style={{ display: 'flex', gap: 'var(--space-2)', marginBlockEnd: 'var(--space-2)' }}
          >
            {(['DIRECT', 'GROUP'] as const).map((kind) => (
              <button
                key={kind}
                type="button"
                className="ui-button ui-button-quiet"
                aria-pressed={composing === kind}
                onClick={() => setComposing(kind)}

                style={{
                  flex: 1,
                  background: composing === kind ? 'var(--surface-inset)' : undefined,
                  color: composing === kind ? 'var(--ink-900)' : undefined,
                }}
              >
                {kind === 'DIRECT' ? t('chat.rail.newDirect') : t('chat.rail.newGroup')}
              </button>
            ))}
          </div>

          {composing === 'DIRECT' ? (
            <select
              className="ui-control"
              aria-label={t('chat.rail.startWith')}
              value=""
              disabled={start.isPending}
              onChange={(event) => {
                const personId = event.target.value;
                if (personId !== '') {
                  start.mutate(personId, {
                    onSuccess: (row) => {
                      setComposing(undefined);
                      onSelect(row.id);
                    },
                  });
                }
              }}
              style={{ width: '100%' }}
            >
              <option value="">{t('chat.rail.choosePerson')}</option>

              {people
                .filter((person) => person.id !== viewerId)
                .map((person) => (
                  <option key={person.id} value={person.id}>
                    {person.displayName}
                  </option>
                ))}
            </select>
          ) : (
            <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <input
                className="ui-control"
                aria-label={t('chat.rail.groupName')}
                placeholder={t('chat.rail.groupNamePlaceholder')}
                value={groupName}
                maxLength={80}
                onChange={(event) => setGroupName(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    openGroup();
                  }
                }}
                style={{ flex: 1, minWidth: 0 }}
              />

              <button
                type="button"
                className="ui-button ui-button-secondary"
                disabled={groupName.trim() === '' || createGroup.isPending}
                onClick={openGroup}
              >
                {t('chat.rail.create')}
              </button>
            </div>
          )}
        </div>
      ) : null}

      <ConversationRail
        conversations={filtered}
        joinable={filteredJoinable}
        onJoin={(conversationId) =>
          membership.mutate(
            { conversationId, join: true },
            { onSuccess: () => onSelect(conversationId) },
          )
        }
        joining={membership.isPending}
        selected={selected}
        onSelect={onSelect}
        loading={conversations.isPending}
      />
    </div>
  );
}

export interface BuildProcessDialogProps {
  draft: WorkDraft;
  onClose: () => void;
  onSubmit: (agreed: AgreedWork) => void;
  busy: boolean;
  refusal?: string | null;
}

interface ChatConversationProps {
  conversationId: string;

  onBack: () => void;

  ConversionDialog: (props: ConversionDialogProps) => JSX.Element;

  onOpenTask: (taskId: string) => void;

  onOpenRun: (instanceId: string) => void;

  BuildProcessDialog?: (props: BuildProcessDialogProps) => JSX.Element;

  onOpenPerson?: (personId: string) => void;

  workSuggestionFor?: (conversationId: string) => ReactNode;

  noticeForSent?: (
    said: string | undefined,

    withWhom: { id: string; name: string } | null,
  ) => ReactNode;

  markFor?: (messageId: string, authorId: string) => ReactNode;

  focusMessageId?: string;

  onFocusUsed?: () => void;
}

export function ChatConversation({
  conversationId,
  onBack,
  ConversionDialog,
  onOpenTask,
  onOpenRun,
  BuildProcessDialog,
  onOpenPerson,
  workSuggestionFor,
  noticeForSent,
  markFor,
  focusMessageId,
  onFocusUsed,
}: ChatConversationProps): JSX.Element {
  const { t } = useTranslation();
  const conversations = useConversations();
  const messages = useMessages(conversationId);
  const markRead = useMarkRead();
  const assignment = useAssignmentContext(conversationId);
  const assignTask = useAssignTask(conversationId);

  const [converting, setConverting] = useState<string | undefined>(undefined);
  const [giving, setGiving] = useState(false);
  const [startingRun, setStartingRun] = useState(false);

  const [ticked, setTicked] = useState<readonly string[] | undefined>(undefined);
  const draftFromMessages = useProcessDraftFromMessages(conversationId);
  const buildProcess = useBuildProcessFromMessages(conversationId);

  const chosen = conversations.data?.conversations.find((row) => row.id === conversationId);

  const newest = messages.data?.messages[0]?.id;
  useEffect(() => {
    if (newest !== undefined) {
      markRead.mutate({ conversationId, throughMessageId: newest });
    }

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId, newest]);

  return (
    <section
      style={{
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-3)',
          padding: 'var(--space-3) var(--space-6)',
          background: 'var(--surface)',
          borderBlockEnd: '1px solid var(--line)',
          flexShrink: 0,
        }}
      >
        <button
          type="button"
          className="fo-icon-button"
          style={{ border: '1px solid var(--line)' }}
          aria-label={t('chat.header.back')}
          onClick={onBack}
        >
          <Icon name="chevronLeft" size={12} strokeWidth={1.6} />
        </button>

        {chosen === undefined || chosen.kind === 'ANNOUNCEMENT' ? null : chosen.kind ===
          'DIRECT' ? (
          <Avatar
            id={chosen.counterpartId ?? chosen.id}
            name={chosen.counterpartName ?? ''}
            size={34}
          />
        ) : (
          <RoomGlyph kind={chosen.kind} />
        )}
        <div style={{ minWidth: 0 }}>
          {onOpenPerson !== undefined &&
          chosen?.kind === 'DIRECT' &&
          chosen.counterpartId !== null ? (
            <button
              type="button"
              onClick={() => onOpenPerson(chosen.counterpartId as string)}
              style={{
                margin: 0,
                padding: 0,
                border: 'none',
                background: 'none',
                font: 'inherit',
                fontSize: 'var(--text-md)',
                fontWeight: 600,
                color: 'var(--ink)',
                cursor: 'pointer',
                textAlign: 'start',
              }}
            >
              {chosen.counterpartName ?? t('chat.rail.formerMember')}
            </button>
          ) : (
            <h2 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 600 }}>
              {chosen === undefined
                ? ''
                : chosen.kind === 'ANNOUNCEMENT'
                  ? t('chat.rail.announcements')
                  : chosen.kind === 'DIRECT'
                    ? (chosen.counterpartName ?? t('chat.rail.formerMember'))
                    : (chosen.name ?? t('chat.rail.unnamedRoom'))}
            </h2>
          )}

          {chosen === undefined ? null : (
            <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--faint)' }}>
              {t(`chat.header.kind.${chosen.kind}`)}
            </p>
          )}
        </div>

        <AssignWorkControls
          context={assignment.data}
          onGiveTask={() => setGiving(true)}
          onStartProcess={() => setStartingRun(true)}
        />

        {BuildProcessDialog !== undefined &&
        ticked === undefined &&
        draftFromMessages.data === undefined ? (
          <button
            type="button"
            className="ui-button ui-button-quiet"
            onClick={() => setTicked([])}
            style={{ marginInlineStart: 'var(--space-2)', fontSize: 'var(--text-xs)' }}
          >
            {t('chat.selection.start')}
          </button>
        ) : null}
      </header>

      <ThreadFor
        conversationId={conversationId}
        markFor={markFor}
        canReply={chosen === undefined || chosen.kind !== 'DIRECT' || chosen.counterpartActive}
        messages={messages.data?.messages ?? []}
        loading={messages.isPending}
        onOpenTask={onOpenTask}
        onOpenRun={onOpenRun}
        chosen={ticked}
        onToggleChosen={(messageId) =>
          setTicked((current) =>
            toggled(
              current ?? [],
              messageId,
              (messages.data?.messages ?? []).map((row) => row.id).reverse(),
            ),
          )
        }
        workSuggestion={workSuggestionFor?.(conversationId)}
        noticeForSent={noticeForSent}

        withWhom={
          chosen?.counterpartId == null || chosen.counterpartName == null
            ? null
            : { id: chosen.counterpartId, name: chosen.counterpartName }
        }
        focusMessageId={focusMessageId}
        onFocusUsed={onFocusUsed}
      />

      {ticked !== undefined && barIsOffered(ticked) ? (
        <SelectionBar
          chosen={ticked}
          busy={draftFromMessages.isPending}
          onCancel={() => setTicked(undefined)}
          onBuild={() => draftFromMessages.mutate(ticked)}
        />
      ) : null}

      {BuildProcessDialog !== undefined && draftFromMessages.data !== undefined ? (
        <BuildProcessDialog
          draft={draftFromMessages.data}
          busy={buildProcess.isPending}
          onClose={() => {
            draftFromMessages.reset();
            setTicked(undefined);
          }}
          onSubmit={(agreed: AgreedWork) =>
            buildProcess.mutate(
              {
                name: agreed.title,
                steps: agreed.steps.map((step) => ({
                  messageId: step.quotedFrom,
                  title: step.title,

                  description:
                    draftFromMessages.data?.steps.find(
                      (from) => from.quotedFrom === step.quotedFrom,
                    )?.description ?? step.title,
                  assigneeId: step.assigneeId,
                  deadline: step.deadline,
                })),
              },
              {
                onSuccess: (created) => {
                  draftFromMessages.reset();
                  setTicked(undefined);
                  if (created.instanceId !== undefined) {
                    onOpenRun(created.instanceId);
                  }
                },
              },
            )
          }
        />
      ) : null}

      {converting === undefined ? null : (
        <ConversionFor
          conversationId={conversationId}
          messageId={converting}
          onClose={() => setConverting(undefined)}
          ConversionDialog={ConversionDialog}
        />
      )}

      {giving && assignment.data?.counterpartId != null ? (
        <ConversionDialog
          open
          prefill={{ title: '', description: '', assigneeId: assignment.data.counterpartId }}
          processes={[]}
          assigneeLocked
          onClose={() => setGiving(false)}
          converting={assignTask.isPending}
          onConvert={(task, outcome) => {
            assignTask.mutate(
              {
                title: task.title,
                description: task.description,

                deadline: task.deadline,
                priority: task.priority,
              },
              {
                onSuccess: () => {
                  outcome.onSuccess();
                  setGiving(false);
                },
                onError: outcome.onError,
              },
            );
          }}
        />
      ) : null}

      {startingRun && assignment.data !== undefined ? (
        <StartRunDialog
          conversationId={conversationId}
          context={assignment.data}
          onClose={() => setStartingRun(false)}
          onStarted={onOpenRun}
        />
      ) : null}
    </section>
  );
}

function ThreadFor({
  conversationId,
  canReply,
  messages,
  loading,
  onOpenTask,
  onOpenRun,
  chosen,
  onToggleChosen,
  workSuggestion,
  noticeForSent,
  withWhom,
  markFor,
  focusMessageId,
  onFocusUsed,
}: {
  conversationId: string;
  markFor?: (messageId: string, authorId: string) => ReactNode;
  focusMessageId?: string;
  onFocusUsed?: () => void;
  canReply: boolean;
  messages: readonly import('../api/chatApi').MessageRow[];
  loading: boolean;
  onOpenTask: (taskId: string) => void;
  onOpenRun: (instanceId: string) => void;
  chosen: readonly string[] | undefined;
  onToggleChosen: (messageId: string) => void;
  workSuggestion: ReactNode;
  noticeForSent?: (
    said: string | undefined,
    withWhom: { id: string; name: string } | null,
  ) => ReactNode;
  withWhom: { id: string; name: string } | null;
}): JSX.Element {
  const send = useSendMessage(conversationId);

  const [said, setSaid] = useState<string | undefined>(undefined);

  return (
    <>
      {noticeForSent?.(said, withWhom)}
      <MessageThread
        messages={messages}
        loading={loading}
        onSend={
          canReply
            ? (body) => {
                send.mutate(body, { onSuccess: () => setSaid(body) });
              }
            : undefined
        }
        sending={send.isPending}
        failed={send.isError}
        onOpenTask={onOpenTask}
        onOpenRun={onOpenRun}
        chosen={chosen}
        onToggleChosen={onToggleChosen}
        workSuggestion={workSuggestion}
        markFor={markFor}
        focusMessageId={focusMessageId}
        onFocusUsed={onFocusUsed}
      />
    </>
  );
}

function ConversionFor({
  conversationId,
  messageId,
  onClose,
  ConversionDialog,
}: {
  conversationId: string;
  messageId: string;
  onClose: () => void;
  ConversionDialog: (props: ConversionDialogProps) => JSX.Element;
}): JSX.Element | null {
  const context = useConversionContext(conversationId, messageId);
  const convert = useConvertMessage(conversationId, messageId);

  if (context.data === undefined) {
    return null;
  }

  return (
    <ConversionDialog
      open
      prefill={{
        title: context.data.title,
        description: context.data.description,

        assigneeId: context.data.suggestedAssigneeActive
          ? (context.data.suggestedAssigneeId ?? undefined)
          : undefined,
      }}
      processes={context.data.instances}
      onClose={onClose}
      converting={convert.isPending}
      onConvert={(task, outcome) => {
        convert.mutate(
          {
            title: task.title,
            description: task.description,
            assigneeId: task.assigneeId,

            deadline: task.deadline,
            priority: task.priority,

            instanceId: task.instanceId,
          },
          {
            onSuccess: () => {
              outcome.onSuccess();
              onClose();
            },
            onError: outcome.onError,
          },
        );
      }}
    />
  );
}
