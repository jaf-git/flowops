package com.flowops.discovery.infrastructure.workspace;

import com.flowops.discovery.application.shared.port.PersonRolePort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PersonRoleAdapter implements PersonRolePort {
    private final JdbcTemplate jdbc;

    public PersonRoleAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String ROLE_OF =
            """
            select functional_role_id
            from workspace_membership
            where user_id = ?
            """;

    @Override
    public boolean ownsTheWorkspace(UUID personId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from auth_user where id = ? and role_name = 'OWNER')",
                Boolean.class,
                personId));
    }

    @Override
    public Optional<UUID> roleOf(UUID personId) {
        List<UUID> found =
                jdbc.query(ROLE_OF, (row, index) -> row.getObject("functional_role_id", UUID.class), personId);
        return found.isEmpty() ? Optional.empty() : Optional.ofNullable(found.get(0));
    }

    private static final String MANAGER_OF =
            """
            select boss.user_id
            from workspace_membership me
            join workspace_membership boss on boss.id = me.manager_id
            where me.user_id = ?
            """;

    @Override
    public Optional<UUID> managerOf(UUID personId) {
        return jdbc.query(MANAGER_OF, (row, index) -> row.getObject("user_id", UUID.class), personId).stream()
                .findFirst();
    }

    private static final String IS_ACTIVE_MEMBER =
            """
            select count(*)
            from workspace_membership
            where user_id = ?
              and status = 'ACTIVE'
            """;

    @Override
    public boolean isActiveMember(UUID personId) {
        if (personId == null) {
            return false;
        }
        Long active = jdbc.queryForObject(IS_ACTIVE_MEMBER, Long.class, personId);
        return active != null && active > 0;
    }
}
