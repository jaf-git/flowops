import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { AssignablePerson } from '../../../shared/model/people';
import { Button } from '../../../shared/ui/Button';
import { Chip } from '../../../shared/ui/Chip';
import { Field } from '../../../shared/ui/Field';
import { Select } from '../../../shared/ui/Select';
import { Spinner } from '../../../shared/ui/Spinner';
import {
  useSchedulePause,
  useSetSchedule,
  useTemplateSchedules,
} from '../hooks/useTemplateSchedules';
import type { ScheduleCadence, TemplateSchedule } from '../api/templateScheduleApi';

const CADENCES: readonly ScheduleCadence[] = ['DAILY', 'WEEKLY', 'MONTHLY'];
const DAYS_OF_WEEK = [1, 2, 3, 4, 5, 6, 7] as const;

interface SchedulePanelProps {
  templateId: string;

  usable: boolean;
  locale: string;

  assignablePeople: readonly AssignablePerson[];
}

export function SchedulePanel({
  templateId,
  usable,
  locale,
  assignablePeople,
}: SchedulePanelProps): JSX.Element {
  const { t } = useTranslation();
  const schedules = useTemplateSchedules(templateId);
  const setSchedule = useSetSchedule(templateId);
  const togglePause = useSchedulePause(templateId);

  const [adding, setAdding] = useState(false);
  const [assigneeId, setAssigneeId] = useState('');
  const [cadence, setCadence] = useState<ScheduleCadence>('MONTHLY');
  const [dayOfWeek, setDayOfWeek] = useState(1);
  const [dayOfMonth, setDayOfMonth] = useState(1);

  const assignable = assignablePeople;
  const rows = schedules.data ?? [];

  function nameOf(personId: string): string {
    return (
      assignable.find((person) => person.id === personId)?.displayName ??
      t('tasklib.schedule.someone')
    );
  }

  function describe(schedule: TemplateSchedule): string {
    if (schedule.cadence === 'DAILY') {
      return t('tasklib.schedule.everyDay');
    }
    if (schedule.cadence === 'WEEKLY') {
      return t('tasklib.schedule.everyWeek', {
        day: t(`tasklib.schedule.day.${String(schedule.dayOfWeek)}`),
      });
    }
    return t('tasklib.schedule.everyMonth', { day: schedule.dayOfMonth });
  }

  function submit(): void {
    setSchedule.mutate(
      {
        assigneeId,
        cadence,

        ...(cadence === 'WEEKLY' ? { dayOfWeek } : {}),
        ...(cadence === 'MONTHLY' ? { dayOfMonth } : {}),
      },
      {
        onSuccess: () => {
          setAdding(false);
          setAssigneeId('');
        },
      },
    );
  }

  return (
    <section className="fo-schedule" aria-labelledby="schedule-heading">
      <header className="fo-schedule-head">
        <h2 className="fo-usage-band-title" id="schedule-heading">
          {t('tasklib.schedule.heading')}
        </h2>
        {usable && !adding && (
          <Button variant="secondary" onClick={() => setAdding(true)}>
            {t('tasklib.schedule.add')}
          </Button>
        )}
      </header>

      <p className="fo-schedule-lead">{t('tasklib.schedule.lead')}</p>

      {!usable && <p className="fo-usage-none">{t('tasklib.schedule.notUsable')}</p>}

      {schedules.isPending && <Spinner label={t('tasklib.schedule.loading')} />}

      {schedules.isError && <p className="fo-usage-none">{t('tasklib.schedule.loadFailed')}</p>}

      {!schedules.isPending && !schedules.isError && rows.length === 0 && !adding && (
        <p className="fo-usage-none">{t('tasklib.schedule.none')}</p>
      )}

      {rows.length > 0 && (
        <ul className="fo-schedule-list">
          {rows.map((schedule) => (
            <li className="fo-schedule-row" key={schedule.id}>
              <div className="fo-schedule-what">
                <span className="fo-schedule-cadence">{describe(schedule)}</span>
                <span className="fo-schedule-who">
                  {t('tasklib.schedule.to', { name: nameOf(schedule.assigneeId) })}
                </span>
              </div>

              <div className="fo-schedule-when">
                {schedule.active ? (
                  <Chip tone="brand">
                    {t('tasklib.schedule.next', {
                      date: formatDate(schedule.nextOccurrence, locale),
                    })}
                  </Chip>
                ) : (
                  <Chip tone="neutral">{t('tasklib.schedule.paused')}</Chip>
                )}
                <span className="fo-schedule-history">
                  {schedule.lastRaisedOn === null
                    ? t('tasklib.schedule.neverRaised')
                    : t('tasklib.schedule.raised', {
                        count: schedule.timesRaised,
                        date: formatDate(schedule.lastRaisedOn, locale),
                      })}
                </span>
              </div>

              <Button
                variant={schedule.active ? 'destructive' : 'secondary'}
                disabled={togglePause.isPending}
                onClick={() =>
                  togglePause.mutate({ scheduleId: schedule.id, running: schedule.active })
                }
              >
                {schedule.active ? t('tasklib.schedule.pause') : t('tasklib.schedule.resume')}
              </Button>
            </li>
          ))}
        </ul>
      )}

      {adding && (
        <div className="fo-schedule-form">
          <Field id="schedule-assignee" label={t('tasklib.schedule.assignee')} required>
            <Select
              id="schedule-assignee"
              value={assigneeId}
              onChange={(event) => setAssigneeId(event.target.value)}
              options={[
                { value: '', label: t('tasklib.stamp.choosePerson') },
                ...assignable.map((person) => ({ value: person.id, label: person.displayName })),
              ]}
            />
          </Field>

          <Field id="schedule-cadence" label={t('tasklib.schedule.cadenceLabel')}>
            <Select
              id="schedule-cadence"
              value={cadence}
              onChange={(event) => setCadence(event.target.value as ScheduleCadence)}
              options={CADENCES.map((value) => ({
                value,
                label: t(`tasklib.schedule.cadence.${value}`),
              }))}
            />
          </Field>

          {cadence === 'WEEKLY' && (
            <Field id="schedule-dow" label={t('tasklib.schedule.dayOfWeek')}>
              <Select
                id="schedule-dow"
                value={String(dayOfWeek)}
                onChange={(event) => setDayOfWeek(Number(event.target.value))}
                options={DAYS_OF_WEEK.map((day) => ({
                  value: String(day),
                  label: t(`tasklib.schedule.day.${String(day)}`),
                }))}
              />
            </Field>
          )}

          {cadence === 'MONTHLY' && (
            <Field
              id="schedule-dom"
              label={t('tasklib.schedule.dayOfMonth')}

              hint={dayOfMonth > 28 ? t('tasklib.schedule.monthEndHint') : undefined}
            >
              <Select
                id="schedule-dom"
                value={String(dayOfMonth)}
                onChange={(event) => setDayOfMonth(Number(event.target.value))}
                options={Array.from({ length: 31 }, (_unused, index) => ({
                  value: String(index + 1),
                  label: String(index + 1),
                }))}
              />
            </Field>
          )}

          <div className="fo-schedule-actions">
            <Button
              variant="quiet"
              onClick={() => setAdding(false)}
              disabled={setSchedule.isPending}
            >
              {t('tasklib.form.cancel')}
            </Button>
            <Button
              variant="primary"
              onClick={submit}
              disabled={assigneeId === '' || setSchedule.isPending}
            >
              {t('tasklib.schedule.confirm')}
            </Button>
          </div>

          {setSchedule.isError && (
            <p className="fo-usage-none">{t('tasklib.schedule.setFailed')}</p>
          )}
        </div>
      )}
    </section>
  );
}

function formatDate(date: string | null, locale: string): string {
  if (date === null) {
    return '—';
  }
  return new Date(`${date}T00:00:00`).toLocaleDateString(locale, {
    day: 'numeric',
    month: 'long',
  });
}
