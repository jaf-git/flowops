import { apiRequest } from '../../../shared/api/client';

export interface FunctionalRoleRow {
  id: string;
  name: string;
}

export interface DepartmentRow {
  id: string;
  name: string;
  roles: FunctionalRoleRow[];
}

export interface Organisation {
  departments: DepartmentRow[];
}

export async function fetchOrganisation(): Promise<Organisation> {
  return apiRequest<Organisation>('/workspace/organisation');
}

export async function fetchRoleAssignments(): Promise<Record<string, string>> {
  return apiRequest<Record<string, string>>('/workspace/organisation/assignments');
}

export async function createDepartment(name: string): Promise<string> {
  return apiRequest<string>('/workspace/departments', {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export async function renameDepartment(department: { id: string; name: string }): Promise<void> {
  return apiRequest<void>(`/workspace/departments/${encodeURIComponent(department.id)}`, {
    method: 'PUT',
    body: JSON.stringify({ name: department.name }),
  });
}

export async function deleteDepartment(departmentId: string): Promise<void> {
  return apiRequest<void>(`/workspace/departments/${encodeURIComponent(departmentId)}`, {
    method: 'DELETE',
  });
}

export async function createFunctionalRole(role: {
  name: string;
  departmentId: string;
}): Promise<FunctionalRoleRow> {
  return apiRequest<FunctionalRoleRow>('/workspace/functional-roles', {
    method: 'POST',
    body: JSON.stringify(role),
  });
}

export async function renameFunctionalRole(role: { id: string; name: string }): Promise<void> {
  return apiRequest<void>(`/workspace/functional-roles/${encodeURIComponent(role.id)}`, {
    method: 'PUT',
    body: JSON.stringify({ name: role.name }),
  });
}

export async function deleteFunctionalRole(roleId: string): Promise<void> {
  return apiRequest<void>(`/workspace/functional-roles/${encodeURIComponent(roleId)}`, {
    method: 'DELETE',
  });
}

export async function assignFunctionalRole(assignment: {
  membershipId: string;
  functionalRoleId: string | null;
}): Promise<void> {
  return apiRequest<void>(
    `/workspace/people/${encodeURIComponent(assignment.membershipId)}/functional-role`,
    { method: 'PUT', body: JSON.stringify({ functionalRoleId: assignment.functionalRoleId }) },
  );
}
