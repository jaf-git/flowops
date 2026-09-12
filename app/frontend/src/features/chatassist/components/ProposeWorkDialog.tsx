import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { AssignablePerson } from '../../../shared/model/people';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import type { AgreedWork, FieldSource, WorkSuggestion } from '../api/chatAssistApi';
import { SourceMark } from './SourceMark';

interface ProposeWorkDialogProps {
  draft: WorkSuggestion;

  people: readonly AssignablePerson[];
  onClose: () => void;

  onSubmit: (agreed: AgreedWork) => void;
  busy: boolean;

  refusal?: string | null;
}

interface Row {
  quotedFrom: string;
  title: string;
  titleSource: FieldSource;
  assigneeId: string;
  assigneeSource: FieldSource;
  deadline: string;
  deadlineSource: FieldSource;
}

function spacedDay(index: number): string {
  const when = new Date();
  when.setDate(when.getDate() + (index + 1) * 3);
  return when.toISOString().slice(0, 10);
}

function asDay(instant: string | null | undefined): string {
  return instant === null || instant === undefined ? '' : instant.slice(0, 10);
}

function asInstant(day: string): string | null {
  return day === '' ? null : new Date(`${day}T09:00:00Z`).toISOString();
}

export function ProposeWorkDialog({
  draft,
  people,
  onClose,
  onSubmit,
  busy,
  refusal = null,
}: ProposeWorkDialogProps): JSX.Element {
  const { t } = useTranslation();

  const [process, setProcess] = useState(draft.shape === 'PROCESS');

  const choices = people.map((person) => ({ value: person.id, label: person.displayName }));

  const [title, setTitle] = useState(draft.title?.value ?? '');
  const [holder, setHolder] = useState(draft.assigneeId?.value ?? '');
  const [when, setWhen] = useState(asDay(draft.deadline?.value));

  const [rows, setRows] = useState<Row[]>(() =>
    draft.steps.map((step, at) => ({
      quotedFrom: step.quotedFrom,
      title: step.title.value,
      titleSource: step.title.source,

      assigneeId: step.assigneeId?.value ?? draft.assigneeId?.value ?? '',
      assigneeSource: step.assigneeId?.source ?? 'SUGGESTED',
      deadline: asDay(step.deadline?.value) === '' ? spacedDay(at) : asDay(step.deadline?.value),
      deadlineSource: step.deadline?.source ?? 'SUGGESTED',
    })),
  );

  function change(at: number, part: Partial<Row>): void {
    setRows((current) => current.map((row, index) => (index === at ? { ...row, ...part } : row)));
  }

  function drop(at: number): void {
    setRows((current) => current.filter((_, index) => index !== at));
  }

  const incomplete =
    title.trim() === '' ||
    holder === '' ||
    (process &&
      (rows.length === 0 || rows.some((row) => row.title.trim() === '' || row.assigneeId === '')));

  return (
    <Dialog
      open
      onCancel={onClose}
      title={t(process ? 'chatAssist.form.processTitle' : 'chatAssist.form.taskTitle')}
      actions={
        <>
          <button
            type="button"
            className="ui-button ui-button-quiet"
            onClick={onClose}
            disabled={busy}
          >
            {t('chatAssist.form.cancel')}
          </button>
          <button
            type="button"
            className="ui-button ui-button-primary"
            onClick={() =>
              onSubmit({
                shape: process ? 'PROCESS' : 'TASK',
                title: title.trim(),
                assigneeId: holder,
                deadline: asInstant(when),
                steps: rows.map((row) => ({
                  quotedFrom: row.quotedFrom,
                  title: row.title.trim(),
                  assigneeId: row.assigneeId,
                  deadline: asInstant(row.deadline),
                })),
              })
            }
            disabled={busy || incomplete}
          >
            {busy ? t('chatAssist.form.creating') : t('chatAssist.form.create')}
          </button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
        <p
          style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)', lineHeight: 1.45 }}
        >
          {t('chatAssist.form.readItFirst')}
        </p>

        <Field id="chat-assist-shape" label={t('chatAssist.form.shape')}>
          <Select
            id="chat-assist-shape"
            value={process ? 'PROCESS' : 'TASK'}
            options={[
              { value: 'TASK', label: t('chatAssist.form.shapeTask') },
              { value: 'PROCESS', label: t('chatAssist.form.shapeProcess') },
            ]}
            onChange={(event) => setProcess(event.target.value === 'PROCESS')}
          />
          {draft.shape === null ? null : <SourceMark source="SUGGESTED" />}
        </Field>

        <Field
          id="chat-assist-name"
          label={t(process ? 'chatAssist.form.runName' : 'chatAssist.form.taskName')}
        >
          <Input
            id="chat-assist-name"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
          />
          {draft.title === null ? null : <SourceMark source={draft.title.source} />}
        </Field>

        <Field
          id="chat-assist-holder"
          label={t(process ? 'chatAssist.form.steeredBy' : 'chatAssist.form.doneBy')}
        >
          <Select
            id="chat-assist-holder"
            value={holder}
            options={choices}
            onChange={(event) => setHolder(event.target.value)}
          />
          {draft.assigneeId === null ? null : <SourceMark source={draft.assigneeId.source} />}
        </Field>

        {process ? null : (
          <Field id="chat-assist-by" label={t('chatAssist.form.by')}>
            <Input
              id="chat-assist-by"
              type="date"
              value={when}
              onChange={(event) => setWhen(event.target.value)}
            />
            {draft.deadline === null ? null : <SourceMark source={draft.deadline.source} />}
          </Field>
        )}

        <fieldset style={{ border: 0, margin: 0, padding: 0 }}>
          <legend
            style={{
              fontSize: 'var(--text-sm)',
              fontWeight: 600,
              padding: 0,
              marginBottom: 'var(--space-2)',
            }}
          >
            {t(process ? 'chatAssist.form.steps' : 'chatAssist.form.checklist', {
              count: rows.length,
            })}
          </legend>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
            {rows.map((row, at) => (
              <div
                key={`${String(at)}-${row.title}`}
                style={{
                  display: 'flex',
                  gap: 'var(--space-2)',
                  alignItems: 'flex-start',
                  flexWrap: 'wrap',
                }}
              >
                <div style={{ flex: '2 1 14rem', minWidth: 0 }}>
                  <Input
                    id={`chat-assist-step-${String(at)}`}
                    value={row.title}
                    onChange={(event) => change(at, { title: event.target.value })}
                    aria-label={t('chatAssist.form.stepTitle', { number: at + 1 })}
                  />
                  <SourceMark source={row.titleSource} />
                </div>

                {process ? (
                  <>
                    <div style={{ flex: '1 1 9rem', minWidth: 0 }}>
                      <Select
                        id={`chat-assist-step-who-${String(at)}`}
                        value={row.assigneeId}
                        options={choices}
                        onChange={(event) => change(at, { assigneeId: event.target.value })}
                        aria-label={t('chatAssist.form.stepAssignee', { number: at + 1 })}
                      />
                      <SourceMark source={row.assigneeSource} />
                    </div>

                    <div style={{ flex: '1 1 8rem', minWidth: 0 }}>
                      <Input
                        id={`chat-assist-step-when-${String(at)}`}
                        type="date"
                        value={row.deadline}
                        onChange={(event) => change(at, { deadline: event.target.value })}
                        aria-label={t('chatAssist.form.stepDeadline', { number: at + 1 })}
                      />
                      <SourceMark source={row.deadlineSource} />
                    </div>
                  </>
                ) : null}

                <button
                  type="button"
                  className="ui-button ui-button-quiet"
                  onClick={() => drop(at)}
                  aria-label={t('chatAssist.form.removeStep', { number: at + 1 })}
                >
                  {t('chatAssist.form.remove')}
                </button>
              </div>
            ))}
          </div>
        </fieldset>

        {refusal === null ? null : (
          <p role="alert" style={{ margin: 0, color: 'var(--alert)', fontSize: 'var(--text-sm)' }}>
            {refusal}
          </p>
        )}
      </div>
    </Dialog>
  );
}
