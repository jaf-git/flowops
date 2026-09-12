package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.GovernedWorkTypePort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GovernedWorkTypeAdapter implements GovernedWorkTypePort {
    private final JdbcTemplate jdbc;

    public GovernedWorkTypeAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WorkTypeEntry> vocabulary() {
        return jdbc.query(
                """
                select code, label, active, display_order
                  from work_type_vocabulary
                 order by display_order
                """,
                (row, index) -> new WorkTypeEntry(
                        row.getString("code"),
                        row.getString("label"),
                        row.getBoolean("active"),
                        row.getInt("display_order")));
    }

    @Override
    public Map<String, String> aliases() {
        Map<String, String> resolved = new LinkedHashMap<>();
        jdbc.query(
                """
                select lower(btrim(alias)) as alias, code
                  from work_type_alias
                 order by alias
                """,
                row -> {
                    resolved.put(row.getString("alias"), row.getString("code"));
                });
        return Map.copyOf(resolved);
    }

    @Override
    public Map<TypePair, Double> family(int version) {
        Map<TypePair, Double> weights = new LinkedHashMap<>();
        jdbc.query(
                """
                select type_a, type_b, weight
                  from work_type_family
                 where version = ?
                """,
                row -> {
                    weights.put(
                            TypePair.of(
                                    row.getString("type_a").toUpperCase(Locale.ROOT),
                                    row.getString("type_b").toUpperCase(Locale.ROOT)),
                            row.getBigDecimal("weight").doubleValue());
                },
                version);
        return Map.copyOf(weights);
    }
}
