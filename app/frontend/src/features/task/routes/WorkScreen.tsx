import { useState, type ComponentProps, type JSX } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';

import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { BlockTaskDialog } from '../components/BlockTaskDialog';
import {
  DecideDeadlineDialogFromDetail,
  EditTaskDialogFromDetail,
} from '../components/TaskDialogsFromDetail';
import { DeadlineNoticeStrip } from '../components/DeadlineNoticeStrip';
import { ProposeDeadlineDialog } from '../components/ProposeDeadlineDialog';
import { SetDeadlineDialog } from '../components/SetDeadlineDialog';
import { TaskMaterialPanel } from '../components/TaskMaterialPanel';
import { RejectTaskDialog } from '../components/RejectTaskDialog';
import { CompleteTaskDialog } from '../components/CompleteTaskDialog';
import { ReviewQueue } from '../components/ReviewQueue';
import { TaskActionRail } from '../../../shared/ui/TaskActionRail';
import { TaskReviewPanel } from '../components/TaskReviewPanel';
import { DeadlineIndicator } from '../../../shared/ui/DeadlineIndicator';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { RelativeTime } from '../../../shared/ui/RelativeTime';
import { Spinner } from '../../../shared/ui/Spinner';
import { Table, type Column } from '../../../shared/ui/Table';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { SelectionBar } from '../components/SelectionBar';
import { TaskCategoryBar } from '../components/TaskCategoryBar';
import { useTaskCategories } from '../hooks/useTaskCategories';
import { useTaskSelection } from '../hooks/useTaskSelection';
import { FilterBar, type FilterPreset } from '../../../shared/ui/FilterBar';
import { Pagination } from '../../../shared/ui/Pagination';
import { SectionedList } from '../../../shared/ui/SectionedList';
import { useAnnounce } from '../../../shared/notice/useNotices';
import { Select } from '../../../shared/ui/Select';
import { TaskStateChip } from '../../../shared/ui/TaskStateChip';
import {
  chipState,
  type TaskQueryFilter,
  type TaskSectionCount,
  type TaskSectionKey,
  type TaskSortKey,
  type TaskSummary,
} from '../api/taskApi';
import { CreateTaskDialog, type ProcessPlacement } from '../components/CreateTaskDialog';
import {
  useAcceptTask,
  useCloseTask,
  useStartTask,
  useTaskSection,
  useTaskSections,
  useTasks,
  useUnblockTask,
} from '../hooks/useTasks';
import { PageHeader } from '../../../shared/ui/PageHeader';

interface WorkScreenProps {
  permissions: readonly string[];

  processes?: readonly ProcessPlacement[];
  onCreateInProcess?: CreateTaskDialogProps['onCreateInProcess'];
  placingInProcess?: boolean;

  onCreateFromTemplate?: CreateTaskDialogProps['onCreateFromTemplate'];
  templateSuggestions?: CreateTaskDialogProps['templateSuggestions'];

  onOpenTask?: (taskId: string) => void;

  onCreateProcessFrom?: (taskIds: readonly string[]) => void;
}

type CreateTaskDialogProps = ComponentProps<typeof CreateTaskDialog>;

const PRIORITIES = ['URGENT', 'HIGH', 'NORMAL', 'LOW'] as const;

const SORTS: readonly TaskSortKey[] = ['deadline', 'priority', 'recent'];

const PAGE_SIZE = 10;

function TaskSectionRows({
  section,
  filter,
  sort,
  columns,
  caption,
}: {
  section: TaskSectionKey;
  filter: TaskQueryFilter;
  sort: TaskSortKey;
  columns: ReadonlyArray<Column<TaskSummary>>;
  caption: string;
}): JSX.Element {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const result = useTaskSection({ section, filter, sort, page, size: PAGE_SIZE, enabled: true });

  if (result.isPending) {
    return <Spinner label={t('task.loading')} />;
  }

  if (result.isError) {
    return <Banner tone="alert">{t('task.loadFailed')}</Banner>;
  }

  const rows = result.data?.rows ?? [];
  const totalPages = result.data?.totalPages ?? 1;

  return (
    <>
      <Table<TaskSummary>
        caption={caption}
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        empty={
          <EmptyState icon="check" heading={t('task.empty.heading')} body={t('task.empty.body')} />
        }
      />
      <Pagination
        page={page}
        totalPages={totalPages}
        onPage={setPage}
        labels={{
          previous: t('task.pager.previous'),
          next: t('task.pager.next'),
          status: t('task.pager.status', { page: page + 1, of: totalPages }),
        }}
      />
    </>
  );
}

export function WorkScreen({
  permissions,
  processes,
  onCreateInProcess,
  placingInProcess,
  onCreateFromTemplate,
  templateSuggestions,
  onOpenTask,
  onCreateProcessFrom,
}: WorkScreenProps): JSX.Element {
  const { t } = useTranslation();

  const [params, setParams] = useSearchParams();

  const tasks = useTasks();
  const accept = useAcceptTask();
  const start = useStartTask();
  const unblock = useUnblockTask();
  const closeTask = useCloseTask();
  const [creating, setCreating] = useState(false);
  const [reviewing, setReviewing] = useState<string | undefined>(undefined);
  const announce = useAnnounce();

  const setAnnouncement = (message: string): void => announce({ tone: 'done', message });

  const setGiven = (name: string): void =>
    announce({ tone: 'done', message: t('task.create.given', { name }) });

  const [blocking, setBlocking] = useState<string | undefined>(undefined);
  const [completing, setCompleting] = useState<string | undefined>(undefined);
  const [rejecting, setRejecting] = useState<string | undefined>(undefined);
  const [proposing, setProposing] = useState<string | undefined>(undefined);
  const [deciding, setDeciding] = useState<string | undefined>(undefined);
  const [editing, setEditing] = useState<string | undefined>(undefined);
  const [dating, setDating] = useState<string | undefined>(undefined);
  const [material, setMaterial] = useState<string | undefined>(undefined);

  const filter: TaskQueryFilter = {
    q: params.get('q') ?? undefined,
    priority: (params.get('priority') as TaskQueryFilter['priority']) ?? undefined,

    category: params.get('category') ?? undefined,
  };
  const sort = (params.get('sort') as TaskSortKey | null) ?? 'deadline';

  const open = (params.get('open') ?? 'needs-you').split(',').filter((key) => key !== '');
  const everythingIsDefault = params.toString() === '';
  const sections = useTaskSections(filter);

  const groupings = useTaskCategories().data?.categories ?? [];

  const setFilter = (next: TaskQueryFilter): void => {
    const updated = new URLSearchParams(params);
    for (const key of ['q', 'priority', 'category'] as const) {
      const value = next[key];
      if (value === undefined || value === '') {
        updated.delete(key);
      } else {
        updated.set(key, value);
      }
    }
    setParams(updated, { replace: true });
  };

  const setSort = (next: TaskSortKey): void => {
    const updated = new URLSearchParams(params);
    updated.set('sort', next);
    setParams(updated, { replace: true });
  };

  const toggleSection = (key: string): void => {
    const updated = new URLSearchParams(params);
    const next = open.includes(key) ? open.filter((entry) => entry !== key) : [...open, key];
    updated.set('open', next.join(','));
    setParams(updated, { replace: true });
  };

  const presets: FilterPreset[] = [
    { key: 'needs-you', label: t('task.filter.preset.needsYou'), open: 'needs-you' },
    { key: 'overdue', label: t('task.filter.preset.overdue'), open: 'overdue' },
    { key: 'due-soon', label: t('task.filter.preset.thisWeek'), open: 'due-soon' },
  ].map((preset) => ({
    key: preset.key,
    label: preset.label,
    active: open.length === 1 && open[0] === preset.open,
    onPick: () => {
      const updated = new URLSearchParams(params);
      updated.set('open', preset.open);
      setParams(updated, { replace: true });
    },
  }));

  const mayCreate = permissions.includes('TASK_CREATE');

  const inFlightTask =
    (accept.isPending ? accept.variables : undefined) ??
    (start.isPending ? start.variables : undefined) ??
    (unblock.isPending ? unblock.variables.id : undefined) ??
    (closeTask.isPending ? closeTask.variables : undefined);
  const transitionFailed = accept.isError || start.isError || unblock.isError || closeTask.isError;

  const selection = useTaskSelection();

  const choosing = onCreateProcessFrom !== undefined;

  const selectionColumn: Column<TaskSummary> = {
    key: 'selection',
    header: t('task.selection.column'),

    headerHidden: true,
    width: '4%',
    cell: (row) => (
      <Checkbox
        id={`select-task-${row.id}`}
        checked={selection.isChosen(row.id)}
        onChange={() => selection.toggle(row.id)}

        label={t('task.selection.choose', { title: row.title })}
        labelHidden
      />
    ),
  };

  const columns: ReadonlyArray<Column<TaskSummary>> = [
    {
      key: 'title',
      header: t('task.table.title'),

      width: choosing ? '26%' : '30%',

      cell: (row) => (
        <span className="fo-task-title-cell">
          {row.kind === 'TICKET' && (
            <span className="fo-task-mark" title={t('task.ticket.explain')}>
              {t('task.ticket.mark')}
            </span>
          )}

          {onOpenTask === undefined ? (
            <span className="fo-task-row-title">{row.title}</span>
          ) : (
            <button
              type="button"
              className="fo-task-row-title fo-task-row-title--open"
              onClick={() => onOpenTask(row.id)}
            >
              {row.title}
            </button>
          )}

          {row.categoryName !== null && (
            <span className="fo-task-mark" title={t('task.category.filedUnder')}>
              {row.categoryName}
            </span>
          )}
        </span>
      ),
    },
    {
      key: 'assignee',
      header: t('task.table.assignee'),
      width: '12%',

      cell: (row) => (row.assigneeName === '' ? t('task.formerMember') : row.assigneeName),
    },
    {
      key: 'state',
      header: t('task.table.state'),
      width: '11%',
      cell: (row) => <TaskStateChip state={chipState(row.state)} />,
    },
    {
      key: 'deadline',
      header: t('task.table.deadline'),
      width: '12%',

      cell: (row) => <DeadlineIndicator dueAt={row.deadline} atRisk={row.atRisk} />,
    },
    {
      key: 'phase',
      header: t('task.table.phase'),
      width: '13%',

      cell: (row) =>
        row.openPhase === null || row.phaseSince === null ? (
          <span>{t('task.phase.none')}</span>
        ) : (
          <span>
            {t(`task.phase.${row.openPhase}`)} <RelativeTime value={row.phaseSince} />
          </span>
        ),
    },
    {
      key: 'material',
      header: t('task.material.title'),

      width: '12%',

      cell: (row) =>
        row.mine || row.directedByMe ? (
          <Button variant="quiet" onClick={() => setMaterial(row.id)}>
            {t('task.material.open')}
          </Button>
        ) : null,
    },
    {
      key: 'action',
      header: t('task.table.action'),
      width: '10%',
      cell: (row) => (
        <TaskActionRail
          state={row.state}
          mine={row.mine}
          directedByMe={row.directedByMe}
          deadlineProposalOpen={row.deadlineProposalOpen}

          hasDeadline={row.deadline !== null}
          permissions={permissions}
          busy={inFlightTask === row.id}

          scope="queue"
          onReassign={() => onOpenTask?.(row.id)}
          onOverride={() => onOpenTask?.(row.id)}

          onAccept={() => accept.mutate(row.id)}
          onReject={() => setRejecting(row.id)}
          onPropose={() => setProposing(row.id)}
          onSetDeadline={() => setDating(row.id)}
          onDecideDeadline={() => setDeciding(row.id)}
          onEdit={() => setEditing(row.id)}
          onStart={() =>
            start.mutate(row.id, {
              onSuccess: () => setAnnouncement(t('task.moved.started')),
            })
          }
          onBlock={() => setBlocking(row.id)}
          onUnblock={() =>
            unblock.mutate(
              { id: row.id, resolution: '' },
              { onSuccess: () => setAnnouncement(t('task.moved.unblocked')) },
            )
          }
          onComplete={() => setCompleting(row.id)}
          onReview={() => setReviewing(row.id)}
          onClose={() =>
            closeTask.mutate(row.id, {
              onSuccess: () => setAnnouncement(t('task.moved.closed')),
            })
          }
        />
      ),
    },
  ];

  const queueColumns: ReadonlyArray<Column<TaskSummary>> = choosing
    ? [selectionColumn, ...columns]
    : columns;

  return (
    <div className="fo-task-page fo-task-queue">
      <PageHeader
        title={t('task.section.heading')}
        subtitle={t('task.section.subtitle')}
        actions={
          mayCreate ? (
            <Button onClick={() => setCreating(true)}>{t('task.create.open')}</Button>
          ) : undefined
        }
      />

      {transitionFailed && <Banner tone="alert">{t('task.moved.failed')}</Banner>}

      <DeadlineNoticeStrip onChange={(task) => setEditing(task)} />

      <ReviewQueue permissions={permissions} onOpen={(task) => setReviewing(task)} />

      <TaskCategoryBar permissions={permissions} />

      <FilterBar
        query={filter.q ?? ''}
        onQuery={(q) => setFilter({ ...filter, q })}
        presets={presets}
        onClear={everythingIsDefault ? undefined : () => setFilter({})}
        labels={{
          search: t('task.filter.search'),
          searchPlaceholder: t('task.filter.searchPlaceholder'),
          presets: t('task.filter.presets'),
          clear: t('task.filter.clear'),
        }}
      >
        <span className="fo-task-filter-choice">
          <Select
            id="task-filter-priority"
            aria-label={t('task.filter.priority')}
            value={filter.priority ?? ''}
            onChange={(event) =>
              setFilter({
                ...filter,
                priority: (event.target.value || undefined) as TaskQueryFilter['priority'],
              })
            }
            options={[
              { value: '', label: t('task.filter.anyPriority') },
              ...PRIORITIES.map((priority) => ({
                value: priority,
                label: t(`task.priority.${priority}`),
              })),
            ]}
          />
        </span>

        {groupings.length > 0 && (
          <span className="fo-task-filter-choice">
            <Select
              id="task-filter-category"
              aria-label={t('task.filter.category')}
              value={filter.category ?? ''}
              onChange={(event) =>
                setFilter({ ...filter, category: event.target.value || undefined })
              }
              options={[
                { value: '', label: t('task.filter.anyCategory') },
                ...groupings.map((category) => ({ value: category.id, label: category.name })),
              ]}
            />
          </span>
        )}
        <span className="fo-task-filter-choice">
          <Select
            id="task-filter-sort"
            aria-label={t('task.filter.sort')}
            value={sort}
            onChange={(event) => setSort(event.target.value as TaskSortKey)}
            options={SORTS.map((option) => ({ value: option, label: t(`task.sort.${option}`) }))}
          />
        </span>
      </FilterBar>

      {sections.isError && <Banner tone="alert">{t('task.loadFailed')}</Banner>}

      {sections.isPending ? (
        <Spinner label={t('task.loading')} />
      ) : (
        <SectionedList
          sections={(sections.data?.sections ?? []).map((section: TaskSectionCount) => ({
            key: section.section,
            label: t(`task.band.${section.section}`),
            count: section.count,
          }))}
          open={open}
          onToggle={toggleSection}
          emptyLabel={
            everythingIsDefault ? (
              <EmptyState
                icon="check"
                heading={t('task.empty.heading')}
                body={t('task.empty.body')}
              />
            ) : (
              t('task.filter.noMatches')
            )
          }
          renderSection={(key) => (
            <TaskSectionRows
              section={key as TaskSectionKey}
              filter={filter}
              sort={sort}
              columns={queueColumns}
              caption={t(`task.band.${key}`)}
            />
          )}
        />
      )}

      {creating && (
        <CreateTaskDialog
          open
          onClose={() => setCreating(false)}
          onCreated={(task) => setGiven(task.assigneeName)}
          processes={processes}
          onCreateInProcess={onCreateInProcess}
          placing={placingInProcess}
          onCreateFromTemplate={onCreateFromTemplate}
          templateSuggestions={templateSuggestions}
        />
      )}

      {blocking !== undefined && (
        <BlockTaskDialog
          key={blocking}
          taskId={blocking}
          onClose={() => setBlocking(undefined)}
          onBlocked={() => setAnnouncement(t('task.moved.blocked'))}
        />
      )}

      {completing !== undefined && (
        <CompleteTaskDialog
          key={completing}
          taskId={completing}
          onClose={() => setCompleting(undefined)}
          onCompleted={() => setAnnouncement(t('task.moved.completed'))}
        />
      )}

      {rejecting !== undefined && (
        <RejectTaskDialog
          key={rejecting}
          taskId={rejecting}
          onClose={() => setRejecting(undefined)}
          onRejected={() => setAnnouncement(t('task.moved.rejected'))}
        />
      )}

      {material !== undefined && (
        <Dialog
          open
          onCancel={() => setMaterial(undefined)}
          title={t('task.material.title')}
          actions={
            <Button variant="quiet" onClick={() => setMaterial(undefined)} data-dialog-cancel>
              {t('task.material.cancel')}
            </Button>
          }
        >
          <TaskMaterialPanel
            taskId={material}
            mine={tasks.data?.tasks.find((row) => row.id === material)?.mine ?? false}
          />
        </Dialog>
      )}

      {dating !== undefined && (
        <SetDeadlineDialog
          key={dating}
          taskId={dating}
          currentDeadline={tasks.data?.tasks.find((row) => row.id === dating)?.deadline ?? null}
          onClose={() => setDating(undefined)}
          onSet={() => setAnnouncement(t('task.moved.deadlineSet'))}
        />
      )}

      {proposing !== undefined && (
        <ProposeDeadlineDialog
          key={proposing}
          taskId={proposing}
          onClose={() => setProposing(undefined)}
          onProposed={() => setAnnouncement(t('task.moved.deadlineProposed'))}
        />
      )}

      {deciding !== undefined && (
        <DecideDeadlineDialogFromDetail
          key={deciding}
          taskId={deciding}
          onClose={() => setDeciding(undefined)}
          onDecided={() => setAnnouncement(t('task.moved.deadlineDecided'))}
        />
      )}

      {editing !== undefined && (
        <EditTaskDialogFromDetail
          key={editing}
          taskId={editing}
          onClose={() => setEditing(undefined)}
          onEdited={() => setAnnouncement(t('task.moved.edited'))}
        />
      )}

      {reviewing !== undefined && (
        <TaskReviewPanel
          key={reviewing}
          taskId={reviewing}
          onClose={() => setReviewing(undefined)}
          onApproved={() => setAnnouncement(t('task.moved.approved'))}
          onReturned={() => setAnnouncement(t('task.moved.returned'))}
        />
      )}

      {onCreateProcessFrom !== undefined && (
        <SelectionBar
          selection={selection}
          onCreateProcess={() => {
            onCreateProcessFrom(selection.chosen);

            selection.clear();
          }}
        />
      )}
    </div>
  );
}
