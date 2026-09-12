insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'TASK_DECIDE_DEADLINE'),
    ('OWNER', 'TASK_EDIT'),
    ('MANAGER', 'TASK_DECIDE_DEADLINE'),
    ('MANAGER', 'TASK_EDIT');

alter table task alter column assignee_user_id drop not null;

drop index task_assignee_ix;
create index task_assignee_ix on task (assignee_user_id, state) where assignee_user_id is not null;

create table task_deadline_proposal (
    id uuid primary key,
    task_id uuid not null references task (id),

    proposed_deadline timestamptz not null,

    reason text not null,
    proposer_user_id uuid not null references auth_user (id),
    proposed_at timestamptz not null,

    decision varchar(20),

    decision_reason text,
    decided_by_user_id uuid references auth_user (id),
    decided_at timestamptz,
    constraint task_deadline_proposal_reason_not_blank check (length(btrim(reason)) > 0),
    constraint task_deadline_proposal_decision_known
        check (decision is null or decision in ('ACCEPTED', 'DECLINED')),

    constraint task_deadline_proposal_decided_wholly
        check ((decision is null and decided_by_user_id is null and decided_at is null)
            or (decision is not null and decided_by_user_id is not null and decided_at is not null)),

    constraint task_deadline_proposal_decline_says_why
        check (decision is distinct from 'DECLINED' or length(btrim(coalesce(decision_reason, ''))) > 0)
);

create unique index task_deadline_proposal_one_open_ix
    on task_deadline_proposal (task_id)
    where decision is null;

create index task_deadline_proposal_task_ix on task_deadline_proposal (task_id, proposed_at desc);

create table task_amendment (
    id uuid primary key,
    task_id uuid not null references task (id),

    event_id uuid not null unique references task_event (id),

    former_deadline timestamptz,
    new_deadline timestamptz,
    former_priority varchar(20),
    new_priority varchar(20),

    former_description text,
    new_description text,
    actor_user_id uuid not null references auth_user (id),
    occurred_at timestamptz not null,

    constraint task_amendment_changes_something
        check (former_deadline is not null
            or new_deadline is not null
            or former_priority is not null
            or new_priority is not null
            or former_description is not null
            or new_description is not null)
);

create index task_amendment_task_ix on task_amendment (task_id, occurred_at desc);

comment on table task_deadline_proposal is
    'A renegotiation, and its answer. One open per task, held by a partial unique index rather than by a rule.';

comment on table task_amendment is
    'What a field was and what it became. The event says something changed; this says what.';
