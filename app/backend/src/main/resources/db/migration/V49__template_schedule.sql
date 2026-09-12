create table template_schedule (
    id uuid primary key,

    template_id uuid not null references task_template (id),

    assignee_user_id uuid not null references auth_user (id),

    created_by_user_id uuid not null references auth_user (id),

    cadence varchar(10) not null,

    day_of_week integer,

    day_of_month integer,

    active boolean not null default true,

    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint template_schedule_cadence check (cadence in ('DAILY', 'WEEKLY', 'MONTHLY')),

    constraint template_schedule_weekly_names_a_day
        check (cadence <> 'WEEKLY' or (day_of_week between 1 and 7)),
    constraint template_schedule_monthly_names_a_day
        check (cadence <> 'MONTHLY' or (day_of_month between 1 and 31)),
    constraint template_schedule_daily_names_no_day
        check (cadence <> 'DAILY' or (day_of_week is null and day_of_month is null))
);

create index template_schedule_active_ix on template_schedule (template_id) where active;

create table template_schedule_run (
    schedule_id uuid not null references template_schedule (id),

    occurrence_date date not null,

    task_id uuid not null,

    created_at timestamptz not null,

    primary key (schedule_id, occurrence_date)
);

comment on table template_schedule is
    'A standing instruction to raise work from a template (TASKLIB-USE-TEMPLATE-01, recurring). It '
    'creates tasks through TASK''s own use case and holds no authority of its own: the reporting-line '
    'check runs against created_by_user_id at every occurrence.';

comment on table template_schedule_run is
    'One occurrence, raised once. The primary key is the idempotency guarantee — a repeated sweep, a '
    'restart mid-pass and two racing instances all land on it and lose.';

comment on column template_schedule.day_of_month is
    'Clamped to the last day of a shorter month, so 31 means the 28th in February rather than never.';
