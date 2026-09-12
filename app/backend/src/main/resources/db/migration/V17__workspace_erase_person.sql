insert into auth_role_permission (role_name, permission_name)
values ('OWNER', 'PERSON_ERASE');

alter table workspace_membership
    add column erased_at timestamptz;

comment on column workspace_membership.erased_at is
    'When identity was destroyed. Always later than deactivated_at, which erasure requires first.';
