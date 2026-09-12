create table workspace_membership (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),

    user_id uuid not null unique references auth_user (id),
    status varchar(20) not null,

    manager_id uuid references workspace_membership (id),
    joined_at timestamptz not null,
    deactivated_at timestamptz,

    constraint workspace_membership_not_own_manager check (manager_id is null or manager_id <> id)
);

create unique index workspace_membership_single_root_ix
    on workspace_membership (workspace_id)
    where manager_id is null;

create index workspace_membership_manager_ix on workspace_membership (manager_id);

insert into workspace_membership (id, workspace_id, user_id, status, manager_id, joined_at)
select gen_random_uuid(),
       '9c8b6f42-1d55-4a0e-9f3a-0b7c5e2d4a10',
       u.id,
       'ACTIVE',
       null,
       u.created_at
from auth_user u
where u.role_name = 'OWNER'
  and u.setup_completed = true
  and not exists (select 1 from workspace_membership m where m.user_id = u.id);

create table workspace_invitation (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),

    email varchar(320) not null,

    intended_role varchar(40) not null references auth_role (name),
    intended_manager_id uuid not null references workspace_membership (id),
    inviter_membership_id uuid not null references workspace_membership (id),
    state varchar(30) not null,

    token_hash varchar(255) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,

    declined_at timestamptz,
    constraint workspace_invitation_role_below_owner check (intended_role <> 'OWNER')
);

create unique index workspace_invitation_one_open_ix
    on workspace_invitation (workspace_id, lower(email))
    where state in ('AWAITING_APPROVAL', 'SENT');

create index workspace_invitation_declined_ix
    on workspace_invitation (lower(email), declined_at desc)
    where declined_at is not null;

create index workspace_invitation_inviter_ix on workspace_invitation (inviter_membership_id, created_at desc);
