import { useMemo, useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import { Spinner } from '../../../shared/ui/Spinner';
import { DepartmentCard } from '../components/DepartmentCard';
import {
  useAssignFunctionalRole,
  useCreateDepartment,
  useOrganisation,
  useRoleAssignments,
} from '../hooks/useOrganisation';
import { usePeople } from '../hooks/usePeople';

const UNSET = '';

export function OrganisationScreen(): JSX.Element {
  const { t } = useTranslation();

  const organisation = useOrganisation();
  const assignments = useRoleAssignments();
  const people = usePeople();

  const createDepartment = useCreateDepartment();
  const assign = useAssignFunctionalRole();

  const [newDepartment, setNewDepartment] = useState('');

  const jobOptions = useMemo(
    () => [
      { value: UNSET, label: t('organisation.notRecorded') },
      ...(organisation.data?.departments ?? []).flatMap((department) =>
        department.roles.map((role) => ({
          value: role.id,

          label: `${role.name} · ${department.name}`,
        })),
      ),
    ],
    [organisation.data, t],
  );

  const active = (people.data?.people ?? []).filter((person) => person.status === 'ACTIVE');

  if (organisation.isPending) {
    return <Spinner label={t('organisation.loading')} />;
  }

  if (organisation.isError) {
    return (
      <p className="fo-org-refused" role="alert">
        {t('organisation.failed')}
      </p>
    );
  }

  const departments = organisation.data?.departments ?? [];

  return (
    <div className="fo-org">
      <header className="fo-org-header">
        <p className="fo-org-eyebrow">{t('organisation.eyebrow')}</p>
        <h1 className="fo-org-title">{t('organisation.title')}</h1>
        <p className="fo-org-lede">{t('organisation.lede')}</p>
      </header>

      {departments.length === 0 ? (
        <EmptyState
          heading={t('organisation.emptyTitle')}
          body={t('organisation.emptyDescription')}
        />
      ) : (
        <div className="fo-org-departments">
          {departments.map((department) => (
            <DepartmentCard department={department} key={department.id} />
          ))}
        </div>
      )}

      <form
        className="fo-org-add"
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          const name = newDepartment.trim();
          if (name !== '') {
            createDepartment.mutate(name, { onSuccess: () => setNewDepartment('') });
          }
        }}
      >
        <label className="fo-org-rename-label" htmlFor="fo-org-new-department">
          {t('organisation.addDepartment')}
        </label>
        <Input
          id="fo-org-new-department"
          value={newDepartment}
          onChange={(event) => {
            setNewDepartment(event.target.value);
          }}
        />
        <Button
          variant="quiet"
          type="submit"
          disabled={newDepartment.trim() === ''}
          loading={createDepartment.isPending}
        >
          {t('organisation.add')}
        </Button>
      </form>

      {createDepartment.isError ? (
        <p className="fo-org-refused" role="alert">
          {createDepartment.error.message}
        </p>
      ) : null}

      <section className="fo-org-who" aria-labelledby="fo-org-who-title">
        <p className="fo-org-eyebrow">{t('organisation.whoEyebrow')}</p>
        <h2 className="fo-org-who-title" id="fo-org-who-title">
          {t('organisation.whoTitle')}
        </h2>
        <p className="fo-org-lede">{t('organisation.whoLede')}</p>

        <ul className="fo-org-people">
          {active.map((person) => (
            <li className="fo-org-person" key={person.membershipId}>
              <span className="fo-org-person-name">{person.displayName}</span>
              <label className="fo-org-rename-label" htmlFor={`fo-org-job-${person.membershipId}`}>
                {t('organisation.jobFor', { name: person.displayName })}
              </label>
              <Select
                id={`fo-org-job-${person.membershipId}`}
                options={jobOptions}
                value={assignments.data?.[person.membershipId] ?? UNSET}
                onChange={(event) => {
                  assign.mutate({
                    membershipId: person.membershipId,

                    functionalRoleId: event.target.value === UNSET ? null : event.target.value,
                  });
                }}
              />
            </li>
          ))}
        </ul>

        {assign.isError ? (
          <p className="fo-org-refused" role="alert">
            {assign.error.message}
          </p>
        ) : null}
      </section>
    </div>
  );
}
