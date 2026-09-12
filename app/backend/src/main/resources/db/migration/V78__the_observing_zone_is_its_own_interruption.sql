alter table notification_preference
    drop constraint notification_preference_group;

alter table notification_preference
    add constraint notification_preference_group
        check (kind_group in ('ASSIGNMENT', 'TIME', 'PROCESS', 'WEEKLY', 'DISCOVERY'));

comment on constraint notification_preference_group on notification_preference is
    'The five groups a person may switch off. DISCOVERY is the observing zone: its notices are about work '
    'somebody is watching rather than work somebody has been given, and conflating the two means muting '
    'task assignments also mutes being told your dependency arrived.';
