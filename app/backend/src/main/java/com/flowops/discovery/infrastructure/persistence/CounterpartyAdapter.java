package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.CounterpartyPort;
import com.flowops.discovery.domain.enums.CounterpartyKind;
import com.flowops.discovery.domain.model.Counterparty;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CounterpartyAdapter implements CounterpartyPort {
    private final JdbcTemplate jdbc;

    public CounterpartyAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID nextId() {
        return UUID.randomUUID();
    }

    @Override
    public void save(Counterparty counterparty) {
        jdbc.update(
                """
                insert into counterparty (id, name, kind, classified_by, classified_at)
                values (?, ?, ?, ?, ?)
                on conflict (id) do update set
                    name          = excluded.name,
                    kind          = excluded.kind,
                    classified_by = excluded.classified_by,
                    classified_at = excluded.classified_at
                """,
                counterparty.id(),
                counterparty.name(),
                counterparty.kind().name(),
                counterparty.classifiedBy().orElse(null),
                counterparty.classifiedAt().map(Timestamp::from).orElse(null));
    }

    @Override
    public Optional<Counterparty> find(UUID id) {
        return jdbc.query("select * from counterparty where id = ?", CounterpartyAdapter::read, id).stream()
                .findFirst();
    }

    @Override
    public List<Counterparty> all() {
        return jdbc.query("select * from counterparty order by lower(name)", CounterpartyAdapter::read);
    }

    @Override
    public Optional<Counterparty> findByName(String name) {
        return jdbc
                .query("select * from counterparty where lower(name) = lower(?)", CounterpartyAdapter::read, name)
                .stream()
                .findFirst();
    }

    @Override
    public long engagementsFor(UUID id) {
        Long count = jdbc.queryForObject("select count(*) from job where counterparty_id = ?", Long.class, id);
        return count == null ? 0 : count;
    }

    @Override
    public void delete(UUID id) {
        jdbc.update("delete from counterparty where id = ?", id);
    }

    private static Counterparty read(ResultSet rs, int row) throws SQLException {
        Timestamp classifiedAt = rs.getTimestamp("classified_at");
        return Counterparty.rehydrated(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                CounterpartyKind.valueOf(rs.getString("kind")),
                rs.getObject("classified_by", UUID.class),
                classifiedAt == null ? null : classifiedAt.toInstant());
    }
}
