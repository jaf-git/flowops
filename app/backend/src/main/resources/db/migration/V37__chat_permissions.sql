insert into auth_permission (name, delegable) values

    ('CHAT_PARTICIPATE', false),

    ('CHAT_POST_ANNOUNCEMENT', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'CHAT_PARTICIPATE'),
    ('MANAGER', 'CHAT_PARTICIPATE'),
    ('EMPLOYEE', 'CHAT_PARTICIPATE');

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'CHAT_POST_ANNOUNCEMENT'),
    ('MANAGER', 'CHAT_POST_ANNOUNCEMENT');
