import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { Dialog } from '../../../shared/ui/Dialog';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { Field } from '../../../shared/ui/Field';
import { IconButton } from '../../../shared/ui/IconButton';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import { Spinner } from '../../../shared/ui/Spinner';
import type { Candidate, Instance } from '../api/processApi';
import { useStartInstanceFromTasks, useTasksForANewProcess } from '../hooks/useProcesses';

interface StartProcessFromTasksDialogProps {
  open: boolean;

  steerers: readonly Candidate[];
  onClose: () => void;

  onStarted: (instance: Instance) => void;

  initialTaskIds?: readonly string[];
}

export function StartProcessFromTasksDialog({
  open,
  steerers,
  onClose,
  onStarted,
  initialTaskIds,
}: StartProcessFromTasksDialogProps): JSX.Element {
  const { t } = useTranslation();
  const offered = useTasksForANewProcess(open);
  const start = useStartInstanceFromTasks();

  const [name, setName] = useState('');
  const [processOwnerId, setProcessOwnerId] = useState('');
  const [chosen, setChosen] = useState<readonly string[]>(initialTaskIds ?? []);

  const [opened, setOpened] = useState(open);
  if (open !== opened) {
    setOpened(open);
    if (open) {
      setChosen(initialTaskIds ?? []);
    }
  }

  const tasks = offered.data?.tasks ?? [];
  const titleOf = (id: string): string => tasks.find((task) => task.id === id)?.title ?? id;

  const unavailable =
    offered.isPending || offered.isError
      ? []
      : chosen.filter((id) => !tasks.some((task) => task.id === id));

  const allChosenAreFinished =
    chosen.length > 0 &&
    chosen.every((id) => tasks.find((task) => task.id === id)?.state === 'CLOSED');

  function close(): void {
    start.reset();
    setName('');
    setProcessOwnerId('');
    setChosen([]);
    onClose();
  }

  function toggle(taskId: string): void {
    setChosen((current) =>
      current.includes(taskId) ? current.filter((each) => each !== taskId) : [...current, taskId],
    );
  }

  function move(from: number, to: number): void {
    setChosen((current) => {
      const moved = current[from];
      if (moved === undefined) {
        return current;
      }
      const next = [...current];
      next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
  }

  function submit(): void {
    start.mutate(
      { name, processOwnerId, taskIds: [...chosen] },
      {
        onSuccess: (started) => {
          onStarted(started);
          close();
        },
      },
    );
  }

  const code =
    start.error instanceof ApiError ? start.error.code : start.error ? 'UNKNOWN' : undefined;

  const missing: string[] = [];
  if (name.trim() === '') {
    missing.push(t('process.startFromTasks.missing.name'));
  }
  if (processOwnerId === '') {
    missing.push(t('process.startFromTasks.missing.owner'));
  }
  if (chosen.length === 0) {
    missing.push(t('process.startFromTasks.missing.tasks'));
  }
  if (unavailable.length > 0) {
    missing.push(
      t('process.startFromTasks.missing.taken', {
        count: unavailable.length,
        titles: unavailable.map(titleOf).join(', '),
      }),
    );
  }

  const incomplete = missing.length > 0;

  const chosenTasks = chosen
    .map((id) => tasks.find((task) => task.id === id))
    .filter((task) => task !== undefined);
  const undated = chosenTasks.filter((task) => task.deadline === null).length;
  const lastLanding =
    chosenTasks.length > 0 && undated === 0
      ? chosenTasks
          .map((task) => task.deadline)
          .filter((deadline): deadline is string => deadline !== null)
          .sort()
          .at(-1)
      : undefined;

  return (
    <Dialog
      open={open}
      onCancel={close}
      title={t('process.startFromTasks.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('process.startFromTasks.cancel')}
          </Button>
          <Button
            variant="primary"
            onClick={submit}
            disabled={incomplete}
            loading={start.isPending}
            loadingLabel={t('process.startFromTasks.submitting')}
          >
            {t('process.startFromTasks.confirm')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.startFromTasks.error.${code}`, t('process.startFromTasks.error.UNKNOWN'))}
        </Banner>
      )}

      <Field
        id="new-process-name"
        label={t('process.startFromTasks.name')}
        hint={t('process.startFromTasks.nameHint')}
        required
      >
        <Input
          id="new-process-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={200}
        />
      </Field>

      <Field
        id="new-process-owner"
        label={t('process.startFromTasks.owner')}
        hint={t('process.startFromTasks.ownerHint')}
        required
      >
        <Select
          id="new-process-owner"
          value={processOwnerId}
          onChange={(event) => setProcessOwnerId(event.target.value)}
          options={[
            { value: '', label: t('process.startFromTasks.choosePerson') },
            ...steerers.map((person) => ({ value: person.id, label: person.displayName })),
          ]}
        />
      </Field>

      <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
        <legend style={{ padding: 0 }}>{t('process.startFromTasks.tasks')}</legend>
        <p style={{ color: 'var(--muted)', margin: '0 0 0.5rem' }}>
          {t('process.startFromTasks.tasksHint')}
        </p>

        {offered.isPending ? <Spinner label={t('process.startFromTasks.loadingTasks')} /> : null}

        {offered.isError ? (
          <Banner tone="alert">{t('process.startFromTasks.tasksLoadFailed')}</Banner>
        ) : tasks.length === 0 && !offered.isPending ? (
          <EmptyState
            heading={t('process.startFromTasks.noTasks.heading')}
            body={t('process.startFromTasks.noTasks.body')}
          />
        ) : (
          tasks.map((task) => (
            <Checkbox
              key={task.id}
              id={`new-process-task-${task.id}`}
              checked={chosen.includes(task.id)}
              onChange={() => toggle(task.id)}
              label={
                task.state === 'CLOSED'
                  ? `${task.title} · ${t('process.startFromTasks.alreadyFinished')}`
                  : task.title
              }
            />
          ))
        )}

        {allChosenAreFinished ? (
          <Banner tone="info">{t('process.startFromTasks.allFinished')}</Banner>
        ) : null}
      </fieldset>

      {chosen.length > 0 ? (
        <section aria-label={t('process.startFromTasks.order')}>
          <h3 style={{ margin: '0 0 0.25rem', fontSize: '1rem' }}>
            {t('process.startFromTasks.order')}
          </h3>
          <p style={{ color: 'var(--muted)', margin: '0 0 0.5rem' }}>
            {t('process.startFromTasks.orderHint')}
          </p>
          <ol className="fo-composer-order">
            {chosen.map((taskId, position) => {
              const task = tasks.find((each) => each.id === taskId);
              const taken = unavailable.includes(taskId);

              return (
                <li className="fo-composer-step" key={taskId}>
                  <span className="fo-composer-what">
                    <span className="fo-composer-title">{titleOf(taskId)}</span>

                    <span className="fo-composer-facts">
                      {taken ? (
                        <span className="fo-composer-taken">
                          {t('process.startFromTasks.rowTaken')}
                        </span>
                      ) : task === undefined ? null : (
                        <>
                          {task.state === 'CLOSED' && (
                            <span>{t('process.startFromTasks.alreadyFinished')}</span>
                          )}
                          <span>
                            {task.deadline === null
                              ? t('process.startFromTasks.rowNoDeadline')
                              : t('process.startFromTasks.rowDue', {
                                  date: new Date(task.deadline).toLocaleDateString(),
                                })}
                          </span>
                        </>
                      )}
                    </span>
                  </span>

                  <span className="fo-composer-move">
                    <IconButton
                      icon="chevronUp"
                      label={t('process.startFromTasks.up', { title: titleOf(taskId) })}
                      disabled={position === 0}
                      onClick={() => {
                        move(position, position - 1);
                      }}
                    />
                    <IconButton
                      icon="chevronDown"
                      label={t('process.startFromTasks.down', { title: titleOf(taskId) })}
                      disabled={position === chosen.length - 1}
                      onClick={() => {
                        move(position, position + 1);
                      }}
                    />
                  </span>
                </li>
              );
            })}
          </ol>
        </section>
      ) : null}

      <footer className="fo-composer-footer">
        <p className="fo-composer-tally">
          {t('process.startFromTasks.tally', { count: chosen.length })}
          {lastLanding !== undefined && (
            <>
              {' · '}
              {t('process.startFromTasks.lastLands', {
                date: new Date(lastLanding).toLocaleDateString(),
              })}
            </>
          )}
          {undated > 0 && (
            <>
              {' · '}
              {t('process.startFromTasks.undated', { count: undated })}
            </>
          )}
        </p>

        {incomplete && (
          <ul className="fo-composer-missing">
            {missing.map((what) => (
              <li key={what}>{what}</li>
            ))}
          </ul>
        )}
      </footer>
    </Dialog>
  );
}
