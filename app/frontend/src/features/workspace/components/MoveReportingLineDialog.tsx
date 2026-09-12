import { useId, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { ConsequencePreview } from '../../../shared/ui/ConsequencePreview';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Select } from '../../../shared/ui/Select';
import { Spinner } from '../../../shared/ui/Spinner';
import type { Person } from '../api/workspaceApi';
import { useReassignPreview, useReassignReportingLine } from '../hooks/usePeople';
import { reassignmentTargets, reportingPathToward } from '../model/reportingTree';

interface MoveReportingLineDialogProps {
  person: Person | undefined;

  people: readonly Person[];
  onClose: () => void;
  onMoved: (personName: string, newManagerName: string) => void;
}

export function MoveReportingLineDialog({
  person,
  people,
  onClose,
  onMoved,
}: MoveReportingLineDialogProps): JSX.Element {
  const { t } = useTranslation();
  const fieldId = useId();
  const [proposedManagerId, setProposedManagerId] = useState<string | undefined>(undefined);

  const preview = useReassignPreview(person?.membershipId, proposedManagerId);
  const move = useReassignReportingLine();

  const targets = person === undefined ? [] : reassignmentTargets(people, person);
  const namesById = new Map(
    people.map((candidate) => [candidate.membershipId, candidate.displayName]),
  );

  function close(): void {
    move.reset();
    setProposedManagerId(undefined);
    onClose();
  }

  function confirm(): void {
    if (person === undefined || proposedManagerId === undefined) {
      return;
    }
    const newManagerName = namesById.get(proposedManagerId) ?? '';
    move.mutate(
      { membershipId: person.membershipId, proposedManagerId },
      {
        onSuccess: (result) => {
          move.reset();

          if (result.changed) {
            onMoved(person.displayName, newManagerName);
          }
          close();
        },
      },
    );
  }

  const loopingPath =
    person === undefined || proposedManagerId === undefined
      ? null
      : reportingPathToward(people, person, proposedManagerId);

  const cycle =
    move.error instanceof ApiError && move.error.code === 'CYCLE' ? move.error : undefined;
  const otherRefusal =
    move.error instanceof ApiError && cycle === undefined ? move.error : undefined;

  const unexpected = move.isError && cycle === undefined && otherRefusal === undefined;

  const explainedPath = cycle === undefined ? loopingPath : cyclePath(cycle);

  return (
    <Dialog
      open={person !== undefined}
      onCancel={close}
      title={t('workspace.move.title')}
      initialFocus="cancel"
      actions={
        <>
          <Button type="button" variant="quiet" data-dialog-cancel onClick={close}>
            {t('workspace.move.cancel')}
          </Button>
          <Button
            type="button"
            onClick={confirm}

            disabled={
              proposedManagerId === undefined ||
              preview.data === undefined ||
              explainedPath !== null
            }
            loading={move.isPending}
            loadingLabel={t('workspace.move.moving')}
          >
            {t('workspace.move.confirm')}
          </Button>
        </>
      }
    >
      <div style={{ display: 'grid', gap: 'var(--space-4)' }}>
        {explainedPath === null ? null : (
          <Banner tone="alert">
            <span style={{ display: 'grid', gap: 'var(--space-2)' }}>
              {explainedPath.map((step, index, path) => {
                const above = path[index + 1];
                return above === undefined ? null : (
                  <span key={step}>
                    {t('workspace.move.cycle.step', {
                      person: namesById.get(above) ?? '',
                      manager: namesById.get(step) ?? '',
                    })}
                  </span>
                );
              })}
              <span>
                {t('workspace.move.cycle.conclusion', {
                  person: person?.displayName ?? '',
                  manager: namesById.get(proposedManagerId ?? '') ?? '',
                })}
              </span>
            </span>
          </Banner>
        )}

        {otherRefusal === undefined ? null : (
          <Banner tone="alert">
            {t(`workspace.move.error.${otherRefusal.code}`, {
              defaultValue: t('workspace.move.error.unexpected'),
            })}
          </Banner>
        )}

        {unexpected ? <Banner tone="alert">{t('workspace.move.error.unexpected')}</Banner> : null}

        <Field id={`${fieldId}-manager`} label={t('workspace.move.managerLabel')} required>
          <Select
            id={`${fieldId}-manager`}
            value={proposedManagerId ?? ''}
            onChange={(event) => {
              move.reset();
              setProposedManagerId(event.target.value === '' ? undefined : event.target.value);
            }}
            options={[
              { value: '', label: t('workspace.move.choose') },
              ...targets.map((candidate) => ({
                value: candidate.membershipId,
                label: candidate.displayName,
              })),
            ]}
          />
        </Field>

        {proposedManagerId === undefined || explainedPath !== null ? null : preview.isPending ? (
          <Spinner label={t('workspace.move.loadingPreview')} />
        ) : preview.isError ? (
          <Banner tone="alert">{t('workspace.move.previewFailed')}</Banner>
        ) : preview.data === undefined ? null : preview.data.alreadyTheirManager ? (
          <Banner tone="info">
            {t('workspace.move.alreadyTheirManager', { manager: preview.data.newManagerName })}
          </Banner>
        ) : (
          <ConsequencePreview
            heading={t('workspace.move.heading', {
              person: preview.data.personName,
              manager: preview.data.newManagerName,
            })}
            consequences={[
              preview.data.formerManagerName === null
                ? t('workspace.move.consequence.gainsAManager', {
                    manager: preview.data.newManagerName,
                  })
                : t('workspace.move.consequence.leaves', {
                    manager: preview.data.formerManagerName,
                  }),
              ...preview.data.movingWithThem.map((moving) =>
                t('workspace.move.consequence.movesToo', { name: moving.displayName }),
              ),
              t('workspace.move.consequence.visibility', { manager: preview.data.newManagerName }),
            ]}
          />
        )}
      </div>
    </Dialog>
  );
}

function cyclePath(failure: ApiError): string[] {
  return failure.details
    .filter((violation) => violation.field === 'step')
    .map((violation) => violation.rule);
}
