import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Card } from '../../../shared/ui/Card';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { Field } from '../../../shared/ui/Field';
import { useAnnounce } from '../../../shared/notice/useNotices';
import { Input } from '../../../shared/ui/Input';
import { Spinner } from '../../../shared/ui/Spinner';
import type { WorkspaceSettingsInput } from '../api/workspaceApi';
import { useUpdateWorkspaceSettings, useWorkspaceSettings } from '../hooks/useWorkspaceSettings';

const DAYS = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

const CARD_TITLE = {
  margin: 0,
  fontSize: 'var(--text-base)',
  fontWeight: 600,
  color: 'var(--ink)',
} as const;

const EXPLAINS = {
  margin: 0,
  fontSize: 'var(--text-md)',
  color: 'var(--muted)',
  lineHeight: 1.6,
} as const;

const RULE_BROKEN: Record<string, string> = {
  ESCALATION_INTERVALS_UNORDERED: 'workspace.settings.error.ladderUnordered',
  AT_RISK_WINDOW_INVALID: 'workspace.settings.error.atRiskWindow',
  QUIET_HOURS_COVER_THE_DAY: 'workspace.settings.error.quietHoursCoverTheDay',
  NO_WORKING_DAYS: 'workspace.settings.error.noWorkingDays',
  TIMEZONE_UNKNOWN: 'workspace.settings.error.timezone',
};

export function SettingsScreen(): JSX.Element {
  const { t } = useTranslation();
  const settings = useWorkspaceSettings();
  const save = useUpdateWorkspaceSettings();
  const [draft, setDraft] = useState<WorkspaceSettingsInput | undefined>(undefined);
  const announce = useAnnounce();

  if (settings.isPending) {
    return (
      <Card>
        <Spinner label={t('workspace.settings.loading')} />
      </Card>
    );
  }

  if (settings.isError || settings.data === undefined) {
    return (
      <Card>
        <EmptyState
          icon="alert"
          heading={t('workspace.settings.error.heading')}
          body={t('workspace.settings.error.body')}
        />
      </Card>
    );
  }

  const current: WorkspaceSettingsInput = draft ?? settings.data;

  const code = save.error instanceof ApiError ? save.error.code : undefined;
  const ruleBroken = code !== undefined ? RULE_BROKEN[code] : undefined;

  const refusal =
    ruleBroken !== undefined
      ? t(ruleBroken)
      : save.error != null
        ? t('workspace.settings.error.couldNotSave')
        : undefined;

  function change(patch: Partial<WorkspaceSettingsInput>): void {
    setDraft({ ...current, ...patch });
  }

  function toggleDay(day: string, on: boolean): void {
    change({
      workingDays: on
        ? [...current.workingDays, day]
        : current.workingDays.filter((selected) => selected !== day),
    });
  }

  function onSave(): void {
    save.mutate(current, {
      onSuccess: (result) => {
        announce({
          tone: 'done',
          message:
            result.changedFields.length === 0
              ? t('workspace.settings.nothingChanged')
              : t('workspace.settings.saved', { count: result.changedFields.length }),
        });
        setDraft(undefined);
      },
    });
  }

  return (
    <div style={{ display: 'grid', gap: 'var(--space-5)', maxWidth: '760px' }}>
      <h2
        style={{
          margin: 0,
          fontSize: 'var(--text-lg)',
          fontWeight: 600,
          letterSpacing: '-0.01em',
        }}
      >
        {t('workspace.settings.title')}
      </h2>

      {refusal === undefined ? null : <Banner tone="alert">{refusal}</Banner>}

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.settings.identity')}</h3>
        <Field id="settings-name" label={t('workspace.settings.name')}>
          <Input
            id="settings-name"
            value={current.name ?? ''}
            onChange={(event) => change({ name: event.target.value })}
          />
        </Field>
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.settings.time')}</h3>

        <p style={EXPLAINS}>{t('workspace.settings.timeExplains')}</p>
        <Field id="settings-timezone" label={t('workspace.settings.timezone')}>
          <Input
            id="settings-timezone"
            value={current.timezone}
            onChange={(event) => change({ timezone: event.target.value })}
          />
        </Field>

        <fieldset
          style={{
            margin: 0,
            padding: 0,
            border: 0,
            display: 'grid',
            gap: 'var(--space-1)',
          }}
        >
          <legend className="fo-eyebrow" style={{ padding: 0 }}>
            {t('workspace.settings.workingDays')}
          </legend>
          {DAYS.map((day) => (
            <Checkbox
              key={day}
              id={`settings-day-${day}`}
              checked={current.workingDays.includes(day)}
              onChange={(on) => toggleDay(day, on)}
              label={t(`workspace.settings.day.${day}`)}
            />
          ))}
        </fieldset>
        <Field id="settings-hours-start" label={t('workspace.settings.workingHoursStart')}>
          <Input
            id="settings-hours-start"
            type="time"
            value={current.workingHoursStart}
            onChange={(event) => change({ workingHoursStart: event.target.value })}
          />
        </Field>
        <Field id="settings-hours-end" label={t('workspace.settings.workingHoursEnd')}>
          <Input
            id="settings-hours-end"
            type="time"
            value={current.workingHoursEnd}
            onChange={(event) => change({ workingHoursEnd: event.target.value })}
          />
        </Field>
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.settings.thresholds')}</h3>
        <Field id="settings-at-risk" label={t('workspace.settings.atRiskWindow')}>
          <Input
            id="settings-at-risk"
            type="number"
            value={String(current.atRiskWindowHours)}
            onChange={(event) => change({ atRiskWindowHours: Number(event.target.value) })}
          />
        </Field>
        <Field
          id="settings-escalation"
          label={t('workspace.settings.escalation')}
          hint={t('workspace.settings.escalationHint')}
        >
          <Input
            id="settings-escalation"
            value={current.escalationIntervalsHours.join(', ')}
            onChange={(event) =>
              change({
                escalationIntervalsHours: event.target.value
                  .split(',')
                  .map((part) => part.trim())
                  .filter((part) => part !== '')
                  .map(Number),
              })
            }
          />
        </Field>
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.settings.analysis')}</h3>

        <p style={EXPLAINS}>{t('workspace.settings.analysisExplains')}</p>
        <Field
          id="settings-closure-coverage"
          label={t('workspace.settings.closureCoverage')}
          hint={t('workspace.settings.closureCoverageHint')}
        >
          <Input
            id="settings-closure-coverage"
            type="number"
            value={String(current.closureCoverageThresholdPercent)}
            onChange={(event) =>
              change({ closureCoverageThresholdPercent: Number(event.target.value) })
            }
          />
        </Field>
        <Field
          id="settings-idle-window"
          label={t('workspace.settings.idleWindow')}
          hint={t('workspace.settings.idleWindowHint')}
        >
          <Input
            id="settings-idle-window"
            type="number"
            value={String(current.templateIdleWindowDays)}
            onChange={(event) => change({ templateIdleWindowDays: Number(event.target.value) })}
          />
        </Field>
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.settings.quietHours')}</h3>

        <p style={EXPLAINS}>{t('workspace.settings.quietHoursExplains')}</p>
        <Field id="settings-quiet-start" label={t('workspace.settings.quietHoursStart')}>
          <Input
            id="settings-quiet-start"
            type="time"
            value={current.quietHoursStart}
            onChange={(event) => change({ quietHoursStart: event.target.value })}
          />
        </Field>
        <Field id="settings-quiet-end" label={t('workspace.settings.quietHoursEnd')}>
          <Input
            id="settings-quiet-end"
            type="time"
            value={current.quietHoursEnd}
            onChange={(event) => change({ quietHoursEnd: event.target.value })}
          />
        </Field>
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.settings.invitations')}</h3>
        <Checkbox
          id="settings-approval"
          checked={current.invitationApprovalRequired}
          onChange={(on) => change({ invitationApprovalRequired: on })}
          label={t('workspace.settings.approvalRequired')}
          disabled
        />
        <p style={EXPLAINS}>{t('workspace.settings.approvalNotYet')}</p>
      </Card>

      <div style={{ display: 'grid', gap: 'var(--space-3)', justifyItems: 'start' }}>
        <p style={EXPLAINS}>{t('workspace.settings.appliesFromNow')}</p>
        <Button
          type="button"
          loading={save.isPending}
          loadingLabel={t('workspace.settings.saving')}
          onClick={onSave}
        >
          {t('workspace.settings.save')}
        </Button>
      </div>
    </div>
  );
}
