insert into auth_permission (name, delegable) values

    ('AI_EXPORT_RUN', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'AI_EXPORT_RUN');
