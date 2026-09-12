package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.TrackClosurePort;
import com.flowops.discovery.domain.enums.CloseReason;
import com.flowops.discovery.domain.enums.Completeness;
import com.flowops.discovery.domain.enums.KeyBasis;
import com.flowops.discovery.domain.enums.TrackState;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackHandover;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.TrackKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TrackClosureAdapter implements TrackClosurePort {
    private final JdbcTemplate jdbc;

    public TrackClosureAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String TRACK_COLUMNS =
            """
            id, job_id, from_role_id, to_role_id, performer_id, solo, key_basis, state,
            opened_at, closed_at, close_reason, disrupted, completeness, last_activity_at,
            continues_track_id, track_type_id, process_template_id
            """;

    private static final String CLOSE_TRACK =
            """
            update track set
                state = ?,
                closed_at = ?,
                close_reason = coalesce(close_reason, ?),
                completeness = coalesce(completeness, ?),
                disrupted = disrupted or ?,
                last_activity_at = ?
            where id = ?
            """;

    @Override
    public void close(Track track) {
        jdbc.update(
                CLOSE_TRACK,
                track.state().name(),
                track.closedAt().map(Timestamp::from).orElse(null),
                track.closeReason().map(Enum::name).orElse(null),
                track.completeness().map(Enum::name).orElse(null),
                track.isDisrupted(),
                Timestamp.from(track.lastActivityAt()),
                track.id().value());
    }

    private static final String RECORD_HANDOVER =
            """
            insert into track_handover (id, track_id, from_performer, to_performer, at, cause)
            values (?, ?, ?, ?, ?, ?)
            """;

    @Override
    public void recordHandover(TrackHandover handover) {
        jdbc.update(
                RECORD_HANDOVER,
                handover.id(),
                handover.trackId().value(),
                handover.fromPerformer(),
                handover.toPerformer(),
                Timestamp.from(handover.at()),
                handover.cause().name());
    }

    private static final String CLOSED_QUALIFYING_TRACKS =
            """
            select %s
            from track
            where job_id = ?
              and closed_at is not null
              and disrupted = false
              and close_reason is not null
              and close_reason not in ('JOB_CLOSED', 'DORMANT')
              and completeness is not null
              and completeness <> 'START_ONLY'
            order by closed_at
            """
                    .formatted(TRACK_COLUMNS);

    @Override
    public List<Track> closedQualifyingTracks(JobId job) {
        return jdbc.query(CLOSED_QUALIFYING_TRACKS, (row, index) -> track(row), job.value());
    }

    private Track track(ResultSet row) throws SQLException {
        TrackKey key = new TrackKey(
                row.getObject("from_role_id", UUID.class),
                row.getObject("to_role_id", UUID.class),
                row.getObject("performer_id", UUID.class),
                KeyBasis.valueOf(row.getString("key_basis")));

        String closeReason = row.getString("close_reason");
        String completeness = row.getString("completeness");

        return Track.rehydrated(
                TrackId.of(row.getObject("id", UUID.class)),
                JobId.of(row.getObject("job_id", UUID.class)),
                key,
                instant(row.getTimestamp("opened_at")),
                TrackState.valueOf(row.getString("state")),
                instant(row.getTimestamp("closed_at")),
                instant(row.getTimestamp("last_activity_at")),
                closeReason == null ? null : CloseReason.valueOf(closeReason),
                completeness == null ? null : Completeness.valueOf(completeness),
                row.getBoolean("disrupted"),
                trackId(row.getObject("continues_track_id", UUID.class)),
                row.getObject("track_type_id", UUID.class),
                row.getObject("process_template_id", UUID.class),
                row.getObject("performer_id", UUID.class));
    }

    private static TrackId trackId(UUID value) {
        return value == null ? null : TrackId.of(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
