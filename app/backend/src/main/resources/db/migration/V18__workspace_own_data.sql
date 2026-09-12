insert into auth_role_permission (role_name, permission_name)
select roles.name, permissions.name
from (values ('OWNER'), ('MANAGER'), ('EMPLOYEE')) as roles (name)
cross join (values ('OWN_DATA_VIEW'), ('OWN_PROFILE_EDIT')) as permissions (name);

create table workspace_data_export (
    id uuid primary key,

    subject_user_id uuid not null references auth_user (id),

    produced_by_user_id uuid not null references auth_user (id),
    produced_at timestamptz not null
);

create index workspace_data_export_subject_ix
    on workspace_data_export (subject_user_id, produced_at desc);
