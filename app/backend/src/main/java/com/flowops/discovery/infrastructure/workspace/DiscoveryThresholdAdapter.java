package com.flowops.discovery.infrastructure.workspace;

import com.flowops.discovery.application.shared.port.DiscoveryThresholdPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryThresholdAdapter implements DiscoveryThresholdPort {
    private final JdbcTemplate jdbc;

    public DiscoveryThresholdAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String THRESHOLDS_IN_FORCE =
            """
            select discovery_tracks_to_propose_type,
                   discovery_tracks_to_form_candidate,
                   discovery_cycles_to_confirm_loop,
                   discovery_ordering_consistency_ratio,
                   discovery_ordering_min_tracks,
                   discovery_continuation_window_days,
                   discovery_role_coverage_floor_percent,
                   discovery_role_degeneracy_percent,
                   discovery_drift_window_completions,
                   discovery_variant_band_low_percent,
                   discovery_variant_band_high_percent,
                   discovery_weight_decay_factor,
                   discovery_pairing_signals_to_auto,
                   discovery_pairing_signals_to_ask
            from workspace_settings
            where effective_to is null
            """;

    @Override
    public DiscoveryThresholds thresholds() {
        return jdbc.queryForObject(
                THRESHOLDS_IN_FORCE,
                (row, index) -> new DiscoveryThresholds(
                        row.getInt("discovery_tracks_to_propose_type"),
                        row.getInt("discovery_tracks_to_form_candidate"),
                        row.getInt("discovery_cycles_to_confirm_loop"),
                        row.getBigDecimal("discovery_ordering_consistency_ratio"),
                        row.getInt("discovery_ordering_min_tracks"),
                        row.getInt("discovery_continuation_window_days"),
                        row.getInt("discovery_role_coverage_floor_percent"),
                        row.getInt("discovery_role_degeneracy_percent"),
                        row.getInt("discovery_drift_window_completions"),
                        row.getInt("discovery_variant_band_low_percent"),
                        row.getInt("discovery_variant_band_high_percent"),
                        row.getBigDecimal("discovery_weight_decay_factor"),
                        row.getInt("discovery_pairing_signals_to_auto"),
                        row.getInt("discovery_pairing_signals_to_ask")));
    }
}
