import { useState, type JSX } from 'react';
import type { AssignablePerson } from '../../../shared/model/people';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';

import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { FilterBar, type FilterPreset } from '../../../shared/ui/FilterBar';
import { useAnnounce } from '../../../shared/notice/useNotices';
import { Pagination } from '../../../shared/ui/Pagination';
import { Spinner } from '../../../shared/ui/Spinner';
import type { TaskTemplate, TemplateSort } from '../api/taskTemplateApi';
import { ApprovalQueueStrip } from '../components/ApprovalQueueStrip';
import { DraftCandidatesStrip } from '../components/DraftCandidatesStrip';
import { TemplateCard } from '../components/TemplateCard';
import { StampTaskDialog } from '../components/StampTaskDialog';
import { TemplateFormDialog } from '../components/TemplateFormDialog';
import { TemplateKindDialog } from '../components/TemplateKindDialog';
import {
  useApprovalQueue,
  useDraftCandidates,
  useApproveTemplate,
  useApproveTemplates,
  useCopyTemplate,
  useCreateTemplate,
  useEditTemplate,
  useSendTemplateBack,
  useRetireTemplate,
  useTemplateLibrary,
  useStampTask,
  useRecordTemplateMetadata,
} from '../hooks/useTaskTemplates';
import { PageHeader } from '../../../shared/ui/PageHeader';

const SORTS: readonly TemplateSort[] = ['MOST_USED', 'NEWEST', 'ALPHABETICAL'];

const PAGE_SIZE = 9;

interface TaskTemplatesScreenProps {
  permissions: readonly string[];

  onAuthorProcess?: () => void;

  assignablePeople: readonly AssignablePerson[];
}

export function TaskTemplatesScreen({
  permissions,
  onAuthorProcess,
  assignablePeople,
}: TaskTemplatesScreenProps): JSX.Element {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();

  const [choosingKind, setChoosingKind] = useState(false);
  const [writing, setWriting] = useState(false);

  const [stamping, setStamping] = useState<TaskTemplate | undefined>(undefined);
  const [editing, setEditing] = useState<TaskTemplate | undefined>(undefined);
  const announce = useAnnounce();
  const setAnnouncement = (message: string): void => announce({ tone: 'done', message });
  const [page, setPage] = useState(0);

  const mayApprove = permissions.includes('TASK_TEMPLATE_APPROVE');
  const mayRetire = permissions.includes('TASK_TEMPLATE_RETIRE');
  const mayCreate = permissions.includes('TASK_TEMPLATE_CREATE');

  const query = {
    q: params.get('q') ?? undefined,
    type: params.get('type') ?? undefined,
    mine: params.get('mine') === 'true',
    sort: (params.get('sort') as TemplateSort | null) ?? 'MOST_USED',
    page,
    size: PAGE_SIZE,
  };

  const library = useTemplateLibrary(query);
  const queue = useApprovalQueue(mayApprove);

  const candidates = useDraftCandidates(mayApprove);

  const create = useCreateTemplate();
  const edit = useEditTemplate();
  const approve = useApproveTemplate();
  const approveMany = useApproveTemplates();
  const sendBack = useSendTemplateBack();
  const retire = useRetireTemplate();
  const copy = useCopyTemplate();
  const stamp = useStampTask();
  const recordMetadata = useRecordTemplateMetadata();

  const busy =
    create.isPending ||
    edit.isPending ||
    approve.isPending ||
    approveMany.isPending ||
    sendBack.isPending ||
    retire.isPending ||
    copy.isPending ||
    stamp.isPending;

  const setParam = (key: string, value: string | undefined): void => {
    const updated = new URLSearchParams(params);
    if (value === undefined || value === '') {
      updated.delete(key);
    } else {
      updated.set(key, value);
    }
    setParams(updated, { replace: true });

    setPage(0);
  };

  const templates = library.data?.templates ?? [];
  const types = library.data?.types ?? [];
  const busiest = templates.reduce((most, template) => Math.max(most, template.timesUsed), 0);

  const presets: FilterPreset[] = [
    {
      key: 'all',
      label: t('tasklib.filter.all'),
      active: !query.mine && query.type === undefined,
      onPick: () => {
        setParams(new URLSearchParams(), { replace: true });
        setPage(0);
      },
    },
    {
      key: 'mine',
      label: t('tasklib.filter.mine'),
      active: query.mine,
      onPick: () => setParam('mine', query.mine ? undefined : 'true'),
    },
    ...types.map((type) => ({
      key: type,
      label: type,
      active: query.type === type,
      onPick: () => setParam('type', query.type === type ? undefined : type),
    })),
  ];

  return (
    <div className="fo-page">
      <PageHeader
        title={t('tasklib.heading')}
        subtitle={t('tasklib.subtitle')}
        actions={
          mayCreate ? (
            <Button onClick={() => setChoosingKind(true)}>{t('tasklib.newTemplate')}</Button>
          ) : undefined
        }
      />

      {mayApprove && candidates.data !== undefined && (
        <DraftCandidatesStrip candidates={candidates.data} />
      )}

      {mayApprove && queue.data !== undefined && (
        <ApprovalQueueStrip
          queue={queue.data}
          knownTypes={types}
          busy={busy}
          onApprove={(id, edited) =>
            approve.mutate(
              { id, edited },
              { onSuccess: () => setAnnouncement(t('tasklib.approved')) },
            )
          }
          onApproveMany={(ids) =>
            approveMany.mutate(ids, {
              onSuccess: () => setAnnouncement(t('tasklib.approvedMany', { count: ids.length })),
            })
          }
          onSendBack={(id, reason) =>
            sendBack.mutate(
              { id, reason },
              { onSuccess: () => setAnnouncement(t('tasklib.rejected')) },
            )
          }
        />
      )}

      <FilterBar
        query={params.get('q') ?? ''}
        onQuery={(value) => setParam('q', value)}
        presets={presets}
        onClear={
          params.toString() === ''
            ? undefined
            : () => {
                setParams(new URLSearchParams(), { replace: true });
                setPage(0);
              }
        }
        labels={{
          search: t('tasklib.filter.search'),
          searchPlaceholder: t('tasklib.filter.searchPlaceholder'),
          presets: t('tasklib.filter.presets'),
          clear: t('tasklib.filter.clear'),
        }}
      >
        <select
          className="ui-control"
          aria-label={t('tasklib.filter.sort')}
          value={query.sort}
          onChange={(event) => setParam('sort', event.target.value)}
        >
          {SORTS.map((option) => (
            <option key={option} value={option}>
              {t(`tasklib.sort.${option}`)}
            </option>
          ))}
        </select>
      </FilterBar>

      {library.isError && <Banner tone="alert">{t('tasklib.loadFailed')}</Banner>}

      {library.isPending ? (
        <Spinner label={t('tasklib.loading')} />
      ) : templates.length === 0 ? (
        <EmptyState
          icon="list"
          heading={
            params.toString() === '' ? t('tasklib.empty.heading') : t('tasklib.empty.filtered')
          }
          body={
            params.toString() === '' ? t('tasklib.empty.body') : t('tasklib.empty.filteredBody')
          }
        />
      ) : (
        <>
          <div className="fo-template-grid">
            {templates.map((template, along) => (
              <TemplateCard
                key={template.id}
                template={template}
                along={along}
                busiest={busiest}
                mayRetire={mayRetire}
                busy={busy}
                onEdit={() => setEditing(template)}
                onUse={() => setStamping(template)}
                onCopy={() =>
                  copy.mutate(template.id, {
                    onSuccess: () => setAnnouncement(t('tasklib.copied')),
                  })
                }
                onRetire={() =>
                  retire.mutate(template.id, {
                    onSuccess: () => setAnnouncement(t('tasklib.retired')),
                  })
                }
              />
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={library.data?.totalPages ?? 1}
            onPage={setPage}
            labels={{
              previous: t('task.pager.previous'),
              next: t('task.pager.next'),
              status: t('task.pager.status', {
                page: page + 1,
                of: library.data?.totalPages ?? 1,
              }),
            }}
          />
        </>
      )}

      {choosingKind && (
        <TemplateKindDialog
          onClose={() => setChoosingKind(false)}
          onTask={() => {
            setChoosingKind(false);
            setWriting(true);
          }}
          onProcess={() => {
            setChoosingKind(false);
            onAuthorProcess?.();
          }}
        />
      )}

      {stamping !== undefined && (
        <StampTaskDialog
          assignablePeople={assignablePeople}
          permissions={permissions}
          key={stamping.id}
          template={stamping}
          busy={busy}
          onClose={() => setStamping(undefined)}
          onStamp={(task, metadata) =>
            stamp.mutate(
              { id: stamping.id, task },
              {
                onSuccess: (created) => {
                  setStamping(undefined);

                  setAnnouncement(t('tasklib.stamped', { title: created.title }));

                  if (metadata !== undefined) {
                    recordMetadata.mutate({ id: stamping.id, ...metadata });
                  }
                },
              },
            )
          }
        />
      )}

      {(writing || editing !== undefined) && (
        <TemplateFormDialog
          key={editing?.id ?? 'new'}
          editing={editing}
          knownTypes={types}
          busy={busy}
          onClose={() => {
            setWriting(false);
            setEditing(undefined);
          }}
          onSubmit={(draft) => {
            const done = {
              onSuccess: () => {
                setWriting(false);
                setEditing(undefined);
                setAnnouncement(
                  draft.submitForApproval ? t('tasklib.submitted') : t('tasklib.saved'),
                );
              },
            };
            if (editing === undefined) {
              create.mutate(draft, done);
            } else {
              edit.mutate({ id: editing.id, draft }, done);
            }
          }}
        />
      )}
    </div>
  );
}
