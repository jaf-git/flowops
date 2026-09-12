create table notification (
    id uuid primary key,

    recipient_user_id uuid not null references auth_user (id),

    kind varchar(40) not null,
    subject_kind varchar(20) not null,
    subject_id uuid not null,
    state varchar(20) not null,
    created_at timestamptz not null,

    deliver_after timestamptz,
    delivered_at timestamptz,
    read_at timestamptz,
    cancelled_at timestamptz,

    cancel_reason varchar(60),
    constraint notification_state check (state in ('QUEUED', 'HELD', 'DELIVERED', 'READ', 'CANCELLED')),

    constraint notification_cancelled_says_why check (
        (state = 'CANCELLED' and cancelled_at is not null and cancel_reason is not null)
            or (state <> 'CANCELLED' and cancelled_at is null and cancel_reason is null)
    ),
    constraint notification_held_has_an_instant check (state <> 'HELD' or deliver_after is not null),
    constraint notification_read_was_delivered check (read_at is null or delivered_at is not null)
);

create unique index notification_no_duplicate_while_pending_ix
    on notification (recipient_user_id, kind, subject_id)
    where state in ('QUEUED', 'HELD', 'DELIVERED');

create index notification_inbox_ix on notification (recipient_user_id, created_at desc);

create index notification_due_for_release_ix
    on notification (deliver_after)
    where state = 'HELD';

create index notification_subject_ix on notification (subject_kind, subject_id) where state in ('QUEUED', 'HELD', 'DELIVERED');

comment on table notification is
    'One thing told to one person. Carries what it is about, never the words -- the sentence is composed '
    'at read time behind the subject''s own access check (NOTIFICATION_03 section 1).';
comment on column notification.deliver_after is
    'The next working instant, for a notice raised inside quiet hours. A held notice is a row with a '
    'future instant and release is a query, so nothing is held in memory and a missed pass loses nothing.';
comment on column notification.cancel_reason is
    'Why a held notice was not sent. Retained rather than deleted: a queue that silently loses rows '
    'cannot be debugged, and a rising count of cancellations means quiet hours are set badly.';

create table notification_preference (
    id uuid primary key,
    person_user_id uuid not null references auth_user (id),
    kind_group varchar(20) not null,
    enabled boolean not null,
    constraint notification_preference_group check (kind_group in ('ASSIGNMENT', 'TIME', 'PROCESS', 'WEEKLY'))
);

create unique index notification_preference_one_per_group_ix
    on notification_preference (person_user_id, kind_group);

comment on table notification_preference is
    'One row per person per group, written only when somebody switches something off. Absent means '
    'enabled. ESCALATION is refused by the check constraint: the ladder has no switch, for anybody.';

alter table workspace_settings

    add column stall_threshold_hours integer not null default 48,

    add column block_threshold_hours integer not null default 48,

    add column review_threshold_hours integer not null default 24,

    add column digest_threshold integer not null default 3;

alter table workspace_settings
    add constraint workspace_settings_reactive_thresholds_positive check (
        stall_threshold_hours > 0
            and block_threshold_hours > 0
            and review_threshold_hours > 0
            and digest_threshold > 0
    );

comment on column workspace_settings.digest_threshold is
    'Notices above this count, releasing to one person at one instant, become one digest. Escalations '
    'are extracted and delivered individually however many there are (NOTIFICATION_03 section 5).';

insert into auth_permission (name, delegable) values
    ('NOTIFICATION_VIEW_OWN', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'NOTIFICATION_VIEW_OWN'),
    ('MANAGER', 'NOTIFICATION_VIEW_OWN'),
    ('EMPLOYEE', 'NOTIFICATION_VIEW_OWN');
