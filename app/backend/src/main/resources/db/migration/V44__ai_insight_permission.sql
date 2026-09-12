insert into auth_permission (name, delegable) values

    ('AI_INSIGHT_VIEW', true);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'AI_INSIGHT_VIEW'),
    ('MANAGER', 'AI_INSIGHT_VIEW');
