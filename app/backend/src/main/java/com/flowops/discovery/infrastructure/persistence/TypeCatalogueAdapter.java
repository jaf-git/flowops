package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.TypeCataloguePort;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.TrackTypeStatus;
import com.flowops.discovery.domain.model.TrackType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TypeCatalogueAdapter implements TypeCataloguePort {
    private final JdbcTemplate jdbc;

    public TypeCatalogueAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String TYPE_COLUMNS =
            """
            id, name, from_role_id, to_role_id, confirmed_by_owner, occurrence_count,
            terminal_output_type, status
            """;

    private static final String SAVE_TYPE =
            """
            insert into track_type (id, name, from_role_id, to_role_id, confirmed_by_owner,
                                    occurrence_count, terminal_output_type, status)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
                name = coalesce(track_type.name, excluded.name),
                confirmed_by_owner = track_type.confirmed_by_owner or excluded.confirmed_by_owner,
                occurrence_count = excluded.occurrence_count,
                terminal_output_type = excluded.terminal_output_type,
                status = excluded.status
            """;

    @Override
    public void save(TrackType type) {
        jdbc.update(
                SAVE_TYPE,
                type.id(),
                type.name().orElse(null),
                type.fromRoleId().orElse(null),
                type.toRoleId().orElse(null),
                type.confirmedByOwner(),
                type.occurrenceCount(),
                type.terminalOutputType().map(Enum::name).orElse(null),
                type.status().name());
    }

    private static final String FIND_TYPE = "select %s from track_type where id = ?".formatted(TYPE_COLUMNS);

    @Override
    public Optional<TrackType> findType(UUID id) {
        return jdbc.query(FIND_TYPE, (row, index) -> type(row), id).stream().findFirst();
    }

    private static final String TYPES_BY_STATUS =
            """
            select %s
            from track_type
            where status in (%s)
            order by occurrence_count desc, id
            """;

    @Override
    public List<TrackType> typesByStatus(TrackTypeStatus... statuses) {
        if (statuses.length == 0) {
            return List.of();
        }
        String placeholders = Arrays.stream(statuses).map(status -> "?").collect(Collectors.joining(", "));
        Object[] names = Arrays.stream(statuses).map(Enum::name).toArray();

        return jdbc.query(TYPES_BY_STATUS.formatted(TYPE_COLUMNS, placeholders), (row, index) -> type(row), names);
    }

    private static final String FIND_BY_NAME =
            """
            select %s
            from track_type
            where name is not null
              and lower(name) = lower(?)
            order by occurrence_count desc, id
            limit 1
            """
                    .formatted(TYPE_COLUMNS);

    @Override
    public Optional<TrackType> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(FIND_BY_NAME, (row, index) -> type(row), name.trim()).stream()
                .findFirst();
    }

    private TrackType type(ResultSet row) throws SQLException {
        String output = row.getString("terminal_output_type");
        return TrackType.rehydrated(
                row.getObject("id", UUID.class),
                row.getString("name"),
                row.getObject("from_role_id", UUID.class),
                row.getObject("to_role_id", UUID.class),
                row.getBoolean("confirmed_by_owner"),
                row.getInt("occurrence_count"),
                output == null ? null : OutputType.valueOf(output),
                TrackTypeStatus.valueOf(row.getString("status")));
    }
}
