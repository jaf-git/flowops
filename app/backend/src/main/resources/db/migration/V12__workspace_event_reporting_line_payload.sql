alter table workspace_event

    add column subject_user_id uuid,

    add column former_manager_user_id uuid,
    add column new_manager_user_id uuid;

create index workspace_event_subject_ix on workspace_event (subject_user_id, occurred_at desc);

alter table workspace_event
    add constraint workspace_event_reporting_line_names_everyone
        check (action <> 'REPORTING_LINE_CHANGED'
            or (subject_user_id is not null
                and former_manager_user_id is not null
                and new_manager_user_id is not null));
