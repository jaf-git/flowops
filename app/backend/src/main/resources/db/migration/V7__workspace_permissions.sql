insert into auth_permission (name, delegable) values

    ('PERSON_INVITE', false);

insert into auth_role_permission (role_name, permission_name)
values ('OWNER', 'PERSON_INVITE');
