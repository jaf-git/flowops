insert into auth_permission (name, delegable) values

    ('WORKSPACE_CONFIGURE', false),
    ('INVITATION_APPROVE', false),
    ('INVITATION_REVOKE_ANY', false),
    ('REPORTING_LINE_EDIT', false),
    ('PERSON_CHANGE_ROLE', false),
    ('PERSON_DEACTIVATE', false),
    ('PERSON_REACTIVATE', false),
    ('PERSON_ERASE', false),
    ('DELEGATION_REVOKE_ANY', false),

    ('PEOPLE_VIEW', true),
    ('ACTIVITY_VIEW', true),
    ('OWN_DATA_VIEW', true),
    ('OWN_PROFILE_EDIT', true),
    ('INVITATION_REVOKE_OWN', true),
    ('DELEGATION_GRANT_OWN', true),
    ('DELEGATION_REVOKE_OWN', true);
