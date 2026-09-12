import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Input } from '../../../shared/ui/Input';
import type { DepartmentRow } from '../api/organisationApi';
import {
  useCreateFunctionalRole,
  useDeleteDepartment,
  useDeleteFunctionalRole,
  useRenameDepartment,
  useRenameFunctionalRole,
} from '../hooks/useOrganisation';
import { RenameForm } from './RenameForm';

export function DepartmentCard({ department }: { department: DepartmentRow }): JSX.Element {
  const { t } = useTranslation();

  const renameDepartment = useRenameDepartment();
  const removeDepartment = useDeleteDepartment();
  const addRole = useCreateFunctionalRole();
  const renameRole = useRenameFunctionalRole();
  const removeRole = useDeleteFunctionalRole();

  const [newRole, setNewRole] = useState('');

  const refusal =
    removeDepartment.error ??
    renameDepartment.error ??
    addRole.error ??
    renameRole.error ??
    removeRole.error;

  return (
    <section className="fo-org-department" aria-labelledby={`fo-org-dept-${department.id}`}>
      <p className="fo-org-eyebrow">{t('organisation.department')}</p>

      <h3 className="fo-org-department-title" id={`fo-org-dept-${department.id}`}>
        {department.name}
      </h3>

      <RenameForm
        id={`fo-org-dept-name-${department.id}`}
        label={t('organisation.departmentName')}
        current={department.name}
        pending={renameDepartment.isPending}
        onRename={(name) => {
          renameDepartment.mutate({ id: department.id, name });
        }}
      />

      <ul className="fo-org-roles">
        {department.roles.map((role) => (
          <li className="fo-org-role" key={role.id}>
            <RenameForm
              id={`fo-org-role-name-${role.id}`}
              label={t('organisation.jobName')}
              current={role.name}
              pending={renameRole.isPending}
              onRename={(name) => {
                renameRole.mutate({ id: role.id, name });
              }}
            />
            <Button
              variant="quiet"
              onClick={() => {
                removeRole.mutate(role.id);
              }}
            >
              {t('organisation.removeJob')}
            </Button>
          </li>
        ))}
      </ul>

      {department.roles.length === 0 ? (
        <p className="fo-org-none">{t('organisation.noJobs')}</p>
      ) : null}

      <form
        className="fo-org-add"
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          const name = newRole.trim();
          if (name !== '') {
            addRole.mutate(
              { name, departmentId: department.id },
              { onSuccess: () => setNewRole('') },
            );
          }
        }}
      >
        <label className="fo-org-rename-label" htmlFor={`fo-org-new-role-${department.id}`}>
          {t('organisation.addJob')}
        </label>
        <Input
          id={`fo-org-new-role-${department.id}`}
          value={newRole}
          onChange={(event) => {
            setNewRole(event.target.value);
          }}
        />
        <Button
          variant="quiet"
          type="submit"
          disabled={newRole.trim() === ''}
          loading={addRole.isPending}
        >
          {t('organisation.add')}
        </Button>
      </form>

      <Button
        variant="quiet"
        loading={removeDepartment.isPending}
        onClick={() => {
          removeDepartment.mutate(department.id);
        }}
      >
        {t('organisation.removeDepartment')}
      </Button>

      {refusal !== null && refusal !== undefined ? (
        <p className="fo-org-refused" role="alert">
          {refusal.message}
        </p>
      ) : null}
    </section>
  );
}
