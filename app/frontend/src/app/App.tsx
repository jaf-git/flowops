import { useQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import {
  AccountPanel,
  AccountStrip,
  AuthGate,
  useSessionContext,
  type SessionContext,
} from '../features/auth';
import {
  OperationsPlane,
  ProcessCanvasScreen,
  ProcessRail,
  bandsFrom,
  bandsOf,
  cardState,
  type CanvasIndexEntry,
} from '../features/canvas';
import {
  ChatConversation,
  ChatRail,
  TaskOriginLink,
  useBringIntoRoom,
  useConversations,
  useParticipants,
  type ConversionDialogProps,
} from '../features/chat';
import { ShapeSuggestionCard } from '../features/aiassist';
import { ProposeWorkDialog, SuggestWorkPanel } from '../features/chatassist';
import {
  ActivityVocabulary,
  CanvasScreen,
  ConversationWorkPanel,
  DigestScreen,
  JobGraphScreen,
  QueueCounters,
  WorkCircles,
  WorkSearch,
  WorkTimeline,
} from '../features/discovery';
import {
  FindingsWorkbench,
  GuidanceScreen,
  PipelineBoard,
  ResemblanceNotice,
  TemplateInsights,
} from '../features/insight';
import {
  AddTaskToProcessDialog,
  AssignStepFromInstance,
  ProcessScreen,
  StartProcessFromTasksDialog,
  useAddTaskToChosenInstance,
  useInstances,
  useCreateProcessCategory,
  ArchiveRunControl,
  useArchiveRun,
  useFileRunUnder,
  useInstancesInFull,
  useProcessCategories,
  type ProcessCategory,
} from '../features/process';
import { ReportsScreen, ThroughputChart, summarise } from '../features/reports';
import {
  CreateTaskDialog,
  MyWorkScreen,
  TaskActionsPanel,
  TaskDrawer,
  TemplateTaskList,
  TodayScreen,
  WorkScreen,
  fetchTemplateTasks,
  fetchThroughput,
  useAssignablePeople,
} from '../features/task';
import {
  TaskTemplatesScreen,
  TemplateSuggestionStrip,
  TemplateUsageScreen,
  useStampTask,
  useTemplateSuggestions,
  TemplatePreviewDialog,
} from '../features/tasklib';
import {
  MyDataScreen,
  OrganisationScreen,
  PeopleScreen,
  SettingsScreen,
  WorkspaceSetupScreen,
  useActiveColleagues,
} from '../features/workspace';
import type { SupportedLocale } from '../i18n';
import { Card } from '../shared/ui/Card';
import { AppShell } from './AppShell';
import { WorkbenchPage } from '../shared/ui/WorkbenchPage';
import type { Section } from './sections';
import { PersonWorkSection } from './PersonWorkSection';
import { WorkPipelinePanel } from './WorkPipelinePanel';

interface AppProps {
  locale: SupportedLocale;

  children?: (session: SessionContext) => JSX.Element;
}

export function App({ locale, children }: AppProps): JSX.Element {
  const { t, i18n } = useTranslation();
  const session = useSessionContext();

  useEffect(() => {
    if (i18n.language !== locale) {
      void i18n.changeLanguage(locale);
    }
  }, [i18n, locale]);

  if (session.isPending) {
    return <Gate>{<p style={{ color: 'var(--muted)' }}>{t('auth.checkingSession')}</p>}</Gate>;
  }

  if (session.data === null || session.data === undefined) {
    return (
      <Gate>
        <AuthGate />
      </Gate>
    );
  }

  if (children !== undefined && session.data.landingTarget !== 'WORKSPACE_SETUP') {
    return children(session.data);
  }

  if (session.data.landingTarget === 'WORKSPACE_SETUP') {
    return <WorkspaceSetupScreen accountStrip={<AccountStrip session={session.data} />} />;
  }

  return <Workspace key={session.data.userId} session={session.data} locale={locale} />;
}

function ProcessSection({ session }: { session: SessionContext }): JSX.Element {
  const colleagues = useActiveColleagues();
  return (
    <ProcessScreen
      insightsFor={(templateId) => <TemplateInsights templateId={templateId} />}
      permissions={session.permissions}
      viewerId={session.userId}
      steerers={colleagues}
    />
  );
}

export function ProcessCanvasSection({
  session,
  instanceId,
  locale,
}: {
  session: SessionContext;
  instanceId: string;
  locale: SupportedLocale;
}): JSX.Element {
  const runs = useInstances();
  const entries: CanvasIndexEntry[] = (runs.data?.instances ?? []).map((run) => ({
    id: run.id,
    name: run.name,
    state: run.state,
    closed: run.progress.closed,
    total: run.progress.total,
  }));

  return (
    <ProcessCanvasScreen
      instanceId={instanceId}
      viewerId={session.userId}
      rail={
        entries.length > 0 ? (
          <ProcessRail entries={entries} currentInstanceId={instanceId} locale={locale} />
        ) : undefined
      }
      renderTaskActions={(subject) => (
        <TaskActionsPanel
          taskId={subject.taskId}
          state={subject.state}
          mine={subject.mine}

          directedByMe={false}
          hasDeadline={subject.hasDeadline}
          currentDeadline={subject.deadline}
          permissions={session.permissions}
        />
      )}

      renderAssign={(subject) => (
        <AssignStepFromInstance
          instanceId={subject.instanceId}
          stepId={subject.stepId}
          onClose={subject.onClose}
          onAssigned={subject.onAssigned}
        />
      )}
    />
  );
}

function TemplatesSection({ permissions }: { permissions: readonly string[] }): JSX.Element {
  const assignable = useAssignablePeople();

  return (
    <TaskTemplatesScreen
      permissions={permissions}
      assignablePeople={assignable.data?.people ?? []}
    />
  );
}

export function TemplateUsageSection({
  session,
  templateId,
  locale,
}: {
  session: SessionContext;
  templateId: string;
  locale: SupportedLocale;
}): JSX.Element {
  const [openTask, setOpenTask] = useState<string | undefined>(undefined);
  const { instances } = useInstancesInFull();

  const assignable = useAssignablePeople();

  const runOf = new Map<string, string>();
  for (const run of instances) {
    for (const step of run.steps) {
      if (step.taskId !== null && step.taskId !== undefined) {
        runOf.set(step.taskId, run.id);
      }
    }
  }

  return (
    <>
      <TemplateUsageScreen
        assignablePeople={assignable.data?.people ?? []}
        templateId={templateId}
        locale={locale}

        onOpenTask={setOpenTask}

        shapeSuggestion={(id, approved) => (
          <ShapeSuggestionCard templateId={id} wanted={approved} />
        )}

        insightsFor={(id) => <TemplateInsights templateId={id} subjectType="task_template" />}
        renderTasks={(band) => (
          <TemplateTasksInBand
            templateId={templateId}
            band={band}
            onOpenTask={setOpenTask}
            canvasHrefFor={(taskId) => {
              const run = runOf.get(taskId);
              return run === undefined ? undefined : `/${locale}/canvas/process/${run}`;
            }}
          />
        )}
      />

      {openTask === undefined ? null : (
        <TaskDrawer
          taskId={openTask}
          viewerId={session.userId}
          permissions={session.permissions}
          onClose={() => setOpenTask(undefined)}
          renderPipeline={(subject) => (
            <WorkPipelinePanel
              taskId={subject.taskId}
              taskTitle={subject.title}
              taskState={subject.state}
              templateId={subject.templateId}
            />
          )}
        />
      )}
    </>
  );
}

function TemplateTasksInBand({
  templateId,
  band,
  onOpenTask,
  canvasHrefFor,
}: {
  templateId: string;
  band: string;
  onOpenTask: (taskId: string) => void;
  canvasHrefFor: (taskId: string) => string | undefined;
}): JSX.Element {
  const rows = useQuery({
    queryKey: ['task-templates', 'tasks', templateId, band],
    queryFn: () => fetchTemplateTasks({ templateId, band }),
    retry: false,
  });

  return (
    <TemplateTaskList
      rows={rows.data ?? []}
      loading={rows.isPending}
      onOpenTask={onOpenTask}
      canvasHrefFor={canvasHrefFor}
    />
  );
}

function Workspace({
  session,
  locale,
}: {
  session: SessionContext;
  locale: SupportedLocale;
}): JSX.Element {
  const { t } = useTranslation();
  const [section, setSection] = useState<Section>('tasks');

  const [conversation, setConversation] = useState<string | undefined>(undefined);

  const [lastConversation, setLastConversation] = useState<string | undefined>(undefined);

  const [focusJob, setFocusJob] = useState<string | undefined>(undefined);

  const [cameFor, setCameFor] = useState<{ conversationId: string; messageId: string } | undefined>(
    undefined,
  );

  function openConversation(id: string): void {
    setConversation(id);
    setLastConversation(id);

    setCameFor(undefined);
  }

  const [viewingPerson, setViewingPerson] = useState<{ id: string; name: string } | undefined>(
    undefined,
  );

  const [openTask, setOpenTask] = useState<string | undefined>(undefined);

  // The shell's own "New process" action. It used to navigate to the Processes section,
  // which read as a dead button to anyone already standing there.
  const [startingProcess, setStartingProcess] = useState(false);
  const mayStartAProcess = session.permissions.includes('PROCESS_INSTANTIATE');

  const colleagues = useActiveColleagues();

  const participants = useParticipants(conversation);
  const roster = (participants.data ?? [])
    .filter((person) => person.active)
    .map((person) => ({ id: person.personId, displayName: person.displayName }));
  const bringIntoRoom = useBringIntoRoom(conversation ?? '');

  const rooms = useConversations();

  const graphConversation = useMemo(
    () =>
      lastConversation ?? rooms.data?.conversations.find((room) => room.lastMessageAt !== null)?.id,
    [lastConversation, rooms.data],
  );

  const nameOfConversation = useCallback(
    (conversationId: string): string | undefined => {
      const room = rooms.data?.conversations.find((one) => one.id === conversationId);
      return room?.name ?? room?.counterpartName ?? undefined;
    },
    [rooms.data],
  );

  const assignable = useAssignablePeople();

  function open(next: Section): void {
    setConversation(undefined);
    setSection(next);
    setCameFor(undefined);
    setFocusJob(undefined);
  }

  function openGraph(jobId: string): void {
    setConversation(undefined);
    setCameFor(undefined);
    setFocusJob(jobId);
    setSection('engagement');
  }

  function openMessage(conversationId: string, messageId: string): void {
    setSection('chat');
    setConversation(conversationId);
    setLastConversation(conversationId);
    setCameFor({ conversationId, messageId });
  }

  return (
    <AppShell
      section={section}
      onSection={open}
      personName={session.email}
      permissions={session.permissions}

      onNewProcess={
        mayStartAProcess
          ? () => {
              setStartingProcess(true);
            }
          : undefined
      }

      onOpenTask={setOpenTask}
    >
      <StartProcessFromTasksDialog
        open={startingProcess}
        steerers={colleagues}
        onClose={() => {
          setStartingProcess(false);
        }}
        onStarted={() => {
          setStartingProcess(false);
          open('processes');
        }}
      />

      {viewingPerson !== undefined ? (
        <PersonWorkSection
          personId={viewingPerson.id}
          displayName={viewingPerson.name}
          onBack={() => setViewingPerson(undefined)}
          onOpenTask={setOpenTask}
          onOpenRun={() => open('processes')}
        />
      ) : section === 'chat' ? (
        <WorkbenchPage
          listLabel={t('shell.nav.chat')}
          list={
            <>
              <QueueCounters permissions={session.permissions} />

              <ChatRail
                people={colleagues}
                viewerId={session.userId}
                selected={conversation}
                onSelect={openConversation}
              />

              <WorkSearch
                onOpenMessage={(conversationId, messageId) => {
                  openConversation(conversationId);
                  setCameFor({ conversationId, messageId });
                }}
              />
            </>
          }
          focus={
            conversation === undefined ? (
              <div className="fo-workbench-empty">
                <p>{t('shell.chat.pickOne')}</p>
              </div>
            ) : (
              <ConversationWorkPanel
                conversationId={conversation}
                onOpenMessage={(messageId) => {
                  setCameFor({ conversationId: conversation, messageId });
                }}

                colleagues={colleagues}
                viewerId={session.userId}

                nameOfConversation={nameOfConversation}
                onOpenConversation={openConversation}
                onOpenGraph={openGraph}
                thread={
                  <ChatConversation
                    conversationId={conversation}
                    onBack={() => setConversation(undefined)}
                    ConversionDialog={ChatConversionDialog}
                    onOpenTask={setOpenTask}

                    onOpenRun={() => open('processes')}
                    onOpenPerson={(personId) =>
                      setViewingPerson({
                        id: personId,
                        name:
                          colleagues.find((person) => person.id === personId)?.displayName ?? '',
                      })
                    }

                    BuildProcessDialog={({ draft, onClose, onSubmit, busy, refusal }) => (
                      <ProposeWorkDialog
                        draft={{ ...draft, available: true }}
                        people={colleagues}
                        onClose={onClose}
                        onSubmit={onSubmit}
                        busy={busy}
                        refusal={refusal}
                      />
                    )}

                    noticeForSent={(said, withWhom) => (
                      <SentNotice said={said} withWhom={withWhom} />
                    )}
                    workSuggestionFor={(id) => (
                      <SuggestWorkPanel
                        conversationId={id}
                        people={assignable.data?.people ?? []}
                      />
                    )}

                    markFor={(messageId, authorId) => (
                      <WorkCircles
                        conversationId={conversation}
                        messageId={messageId}
                        authorId={authorId}
                        viewerId={session.userId}
                        permissions={session.permissions}
                        people={roster}
                        everybody={colleagues}
                        onBringIn={(personId) => bringIntoRoom.mutateAsync(personId)}
                      />
                    )}

                    focusMessageId={
                      cameFor?.conversationId === conversation ? cameFor.messageId : undefined
                    }
                    onFocusUsed={() => setCameFor(undefined)}
                  />
                }
              />
            )
          }
        />
      ) : section === 'canvas' ? (
        <CanvasBoardSection
          onOpenTask={setOpenTask}
          locale={locale}

          names={new Map(colleagues.map((person) => [person.id, person.displayName]))}
        />
      ) : (
        <div style={{ padding: 'var(--space-6) var(--space-7)', flex: 1, minWidth: 0 }}>
          {section === 'account' ? (
            <div style={{ display: 'grid', gap: 'var(--space-5)' }}>
              <AccountPanel session={session} />
              <MyDataScreen />
            </div>
          ) : section === 'people' ? (
            <PeopleScreen />
          ) : section === 'settings' ? (
            <SettingsScreen />
          ) : section === 'organisation' ? (
            <OrganisationScreen />
          ) : section === 'processes' ? (
            <ProcessSection session={session} />
          ) : section === 'reports' ? (
            <ReportsSection locale={locale} />
          ) : section === 'discovery' ? (
            <CanvasScreen
              conversationId={graphConversation}
              permissions={session.permissions}

              onFindWork={() => open('today')}

              onOpenMessage={openMessage}
            />
          ) : section === 'timeline' ? (
            <WorkTimeline onOpenMessage={openMessage} />
          ) : section === 'engagement' ? (
            <JobGraphScreen
              conversationId={graphConversation}

              focusJobId={focusJob}
              onOpenConversation={() => open('today')}
              onFindWork={() => open('today')}

              onOpenMessage={openMessage}
            />
          ) : section === 'weekly' ? (
            <DigestScreen permissions={session.permissions} />
          ) : section === 'pipeline' ? (
            <PipelineBoard />
          ) : section === 'findings' ? (
            <FindingsWorkbench
              onOpenGraph={(conversationId) => {
                setLastConversation(conversationId);
                setSection('engagement');
              }}
              onOpenMessage={(conversationId) => {
                setLastConversation(conversationId);
                setSection('chat');
              }}
            />
          ) : section === 'guidance' ? (
            <>
              <GuidanceScreen />
              <ActivityVocabulary />
            </>
          ) : section === 'tasks' ? (
            <WorkSection permissions={session.permissions} onOpenTask={setOpenTask} />
          ) : section === 'today' ? (
            <TodayScreen onOpenTask={setOpenTask} />
          ) : section === 'myWork' ? (
            <MyWorkScreen onOpenTask={setOpenTask} />
          ) : section === 'templates' ? (
            <TemplatesSection permissions={session.permissions} />
          ) : (
            <NotBuiltYet section={section} />
          )}
        </div>
      )}

      {openTask === undefined ? null : (
        <TaskDrawer
          taskId={openTask}
          viewerId={session.userId}
          permissions={session.permissions}
          onClose={() => setOpenTask(undefined)}

          renderPipeline={(subject) => (
            <WorkPipelinePanel
              taskId={subject.taskId}
              taskTitle={subject.title}
              taskState={subject.state}
              templateId={subject.templateId}
            />
          )}

          origin={
            <TaskOriginLink
              taskId={openTask}
              onOpen={(conversationId) => {
                setOpenTask(undefined);
                openConversation(conversationId);
              }}
            />
          }
        />
      )}
    </AppShell>
  );
}

function ChatConversionDialog({
  open,
  prefill,
  processes,
  assigneeLocked,
  onClose,
  onConvert,
  converting,
}: ConversionDialogProps): JSX.Element {
  return (
    <CreateTaskDialog
      open={open}
      onClose={onClose}
      onCreated={onClose}
      prefill={prefill}
      processes={processes}
      placing={converting}

      assigneeLocked={assigneeLocked}

      onCreateInProcess={(processId, task, outcome) =>
        onConvert({ ...task, instanceId: processId }, outcome)
      }
      onCreateAdHoc={(task, outcome) => onConvert({ ...task, instanceId: null }, outcome)}
    />
  );
}

function Gate({ children }: { children: JSX.Element }): JSX.Element {
  const { t } = useTranslation();

  return (
    <main
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 'var(--space-5)',
        padding: 'var(--space-8) var(--space-6)',
      }}
    >
      <p
        style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 700, letterSpacing: '-0.01em' }}
      >
        {t('app.name')}
      </p>

      <Card style={{ width: '100%', maxWidth: '440px', boxShadow: 'var(--shadow-lg)' }} pad={28}>
        {children}
      </Card>
    </main>
  );
}

function GroupingToolbar({
  categories,
  onName,
  busy,
  refused,
}: {
  categories: readonly ProcessCategory[];
  onName: (name: string) => void;
  busy: boolean;
  refused: boolean;
}): JSX.Element {
  const { t } = useTranslation();
  const [name, setName] = useState('');

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
      {categories.map((category) => (
        <span key={category.id} style={{ fontSize: 'var(--text-xs)', color: 'var(--muted)' }}>
          {t('canvas.group.count', { name: category.name, count: category.runCount })}
        </span>
      ))}
      <input
        className="ui-control"
        aria-label={t('canvas.group.newLabel')}
        placeholder={t('canvas.group.new')}
        value={name}
        onChange={(event) => setName(event.target.value)}
        style={{ maxWidth: 200, marginInlineStart: 'auto' }}
      />
      <button
        type="button"
        className="ui-button ui-button-quiet"
        disabled={name.trim() === '' || busy}
        onClick={() => {
          onName(name.trim());
          setName('');
        }}
      >
        {t('canvas.group.add')}
      </button>

      {refused ? (
        <span style={{ fontSize: 'var(--text-xs)', color: 'var(--alert)' }}>
          {t('canvas.group.taken')}
        </span>
      ) : null}
    </div>
  );
}

function FileRunControl({
  categories,
  chosen,
  onFile,
}: {
  categories: readonly ProcessCategory[];
  chosen: string;
  onFile: (categoryId: string | null) => void;
}): JSX.Element | null {
  const { t } = useTranslation();

  if (categories.length === 0) {
    return null;
  }

  return (
    <select
      className="ui-control"
      aria-label={t('canvas.group.fileLabel')}
      value={chosen}
      onChange={(event) => onFile(event.target.value === '' ? null : event.target.value)}
      style={{ fontSize: 'var(--text-xs)', padding: '2px 4px', maxWidth: 150 }}
    >
      <option value="">{t('canvas.group.none')}</option>
      {categories.map((category) => (
        <option key={category.id} value={category.id}>
          {category.name}
        </option>
      ))}
    </select>
  );
}

function CanvasBoardSection({
  onOpenTask,
  locale,
  names,
}: {
  onOpenTask: (taskId: string) => void;
  locale: SupportedLocale;
  names: ReadonlyMap<string, string>;
}): JSX.Element {
  const { t } = useTranslation();
  const { instances, isPending } = useInstancesInFull();
  const [addingTo, setAddingTo] = useState<string | undefined>(undefined);

  const grouping = useProcessCategories();
  const nameGroup = useCreateProcessCategory();
  const fileRun = useFileRunUnder();
  const archiveRun = useArchiveRun();

  const categories = grouping.data?.categories ?? [];
  const filings = grouping.data?.filings ?? {};

  const groups = bandsOf(
    instances.map((instance) => ({ ...instance, categoryId: filings[instance.id] ?? null })),
    categories,

    (personId) => names.get(personId),
  );

  const target = instances.find((instance) => instance.id === addingTo);

  return (
    <>
      <GroupingToolbar
        categories={categories}
        onName={(name) => nameGroup.mutate(name)}
        busy={nameGroup.isPending}
        refused={nameGroup.isError}
      />
      <OperationsPlane
        sections={groups.map((group) => ({
          key: group.key,
          title: group.name ?? t(`canvas.group.${group.key}`),
          named: group.kind === 'CATEGORY',
          lanes: group.lanes.map((lane) => ({
            key: lane.key,
            personId: lane.personId,

            titled: lane.personId !== undefined,
            bands: bandsFrom(lane.runs),
          })),
        }))}
        loading={isPending}
        locale={locale}
        names={names}
        onAddTask={setAddingTo}
        onOpenTask={onOpenTask}
        runAction={(runId) => (
          <>
            <FileRunControl
              categories={categories}
              chosen={filings[runId] ?? ''}
              onFile={(categoryId) => fileRun.mutate({ instanceId: runId, categoryId })}
            />

            <ArchiveRunControl
              finished={instances.find((instance) => instance.id === runId)?.state !== 'RUNNING'}
              busy={archiveRun.isPending}
              onArchive={() => archiveRun.mutate({ instanceId: runId, archived: true })}
            />
          </>
        )}
      />
      {target === undefined ? null : (
        <AddTaskToProcessDialog
          open
          instance={target}
          onClose={() => setAddingTo(undefined)}
          onAdded={() => setAddingTo(undefined)}
        />
      )}
    </>
  );
}

export function ReportsSection({ locale }: { locale: SupportedLocale }): JSX.Element {
  const { instances, isPending, incomplete } = useInstancesInFull('EVERY_RUN');

  const [span, setSpan] = useState(12);
  const throughput = useQuery({
    queryKey: ['throughput', span],
    queryFn: () => fetchThroughput(span),
    retry: false,
  });

  return (
    <ReportsScreen
      report={summarise(instances, cardState)}
      loading={isPending}
      incomplete={incomplete}

      throughput={
        throughput.data === undefined ? undefined : (
          <ThroughputChart weeks={throughput.data} locale={locale} span={span} onSpan={setSpan} />
        )
      }
      locale={locale}
    />
  );
}

function TemplateSuggestionsFor({
  title,
  offer,
  busy,
  onUse,
}: {
  title: string;
  offer: boolean;
  busy: boolean;
  onUse: (template: { id: string; title: string }) => void;
}): JSX.Element {
  const { i18n } = useTranslation();
  const suggestions = useTemplateSuggestions(title, offer);

  return (
    <TemplateSuggestionStrip
      suggestions={offer ? (suggestions.data ?? []) : []}
      locale={i18n.language}
      busy={busy}
      onUse={(suggestion) => onUse({ id: suggestion.id, title: suggestion.title })}
    />
  );
}

function WorkSection({
  permissions,
  onOpenTask,
}: {
  permissions: readonly string[];
  onOpenTask: (taskId: string) => void;
}): JSX.Element {
  const runs = useInstances();
  const place = useAddTaskToChosenInstance();
  const colleagues = useActiveColleagues();

  const stamp = useStampTask();

  const [composing, setComposing] = useState<readonly string[] | undefined>(undefined);

  const open = (runs.data?.instances ?? []).filter((run) => run.state === 'RUNNING');
  const mayStartARun = permissions.includes('PROCESS_INSTANTIATE');

  return (
    <>
      <StartProcessFromTasksDialog
        open={composing !== undefined}
        steerers={colleagues}
        initialTaskIds={composing}
        onClose={() => {
          setComposing(undefined);
        }}
        onStarted={() => {
          setComposing(undefined);
        }}
      />
      <WorkScreen
        permissions={permissions}

        onCreateProcessFrom={
          mayStartARun
            ? (taskIds) => {
                setComposing(taskIds);
              }
            : undefined
        }

        onOpenTask={onOpenTask}
        processes={open.map((run) => ({ id: run.id, name: run.name }))}
        placingInProcess={place.isPending}
        onCreateFromTemplate={(templateId, task, outcome) => {
          stamp.mutate(
            {
              id: templateId,
              task: {
                title: task.title,
                description: task.description ?? undefined,
                assigneeId: task.assigneeId,
                deadline: task.deadline ?? undefined,
                priority: task.priority,
              },
            },
            outcome,
          );
        }}
        templateSuggestions={({ title, offer, onUse }) => (
          <TemplateSuggestionsFor
            title={title}
            offer={offer}
            busy={stamp.isPending}
            onUse={onUse}
          />
        )}
        onCreateInProcess={(processId, task, outcome) => {
          place.mutate({ instanceId: processId, newTask: task, dependsOnStepIds: [] }, outcome);
        }}
      />
    </>
  );
}

function NotBuiltYet({ section }: { section: Section }): JSX.Element {
  const { t } = useTranslation();

  return (
    <Card style={{ maxWidth: '560px' }}>
      <h2 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 700 }}>
        {t(`shell.nav.${section}`)}
      </h2>
      <p
        style={{
          margin: 'var(--space-3) 0 0',
          color: 'var(--muted)',
          fontSize: 'var(--text-base)',
          lineHeight: 1.6,
        }}
      >
        {t(`shell.pending.${section}`)}
      </p>
    </Card>
  );
}

function SentNotice({
  said,
  withWhom,
}: {
  said: string | undefined;
  withWhom: { id: string; name: string } | null;
}): JSX.Element {
  const [previewing, setPreviewing] = useState<string | undefined>(undefined);

  return (
    <>
      <ResemblanceNotice said={said} onPreview={setPreviewing} />
      {previewing !== undefined && (
        <TemplatePreviewDialog
          templateId={previewing}
          open
          onClose={() => setPreviewing(undefined)}
          withWhom={withWhom}
        />
      )}
    </>
  );
}
