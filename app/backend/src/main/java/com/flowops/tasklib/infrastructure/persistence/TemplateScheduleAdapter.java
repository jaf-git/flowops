package com.flowops.tasklib.infrastructure.persistence;

import com.flowops.tasklib.application.port.TemplateSchedulePort;
import com.flowops.tasklib.domain.Recurrence;
import com.flowops.tasklib.domain.ScheduleCadence;
import com.flowops.tasklib.domain.TemplateSchedule;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class TemplateScheduleAdapter implements TemplateSchedulePort {
    private static final String COLUMNS = "id, template_id, assignee_user_id, created_by_user_id, cadence,"
            + " day_of_week, day_of_month, active, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public TemplateScheduleAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(TemplateSchedule schedule) {
        jdbc.update(
                """
                insert into template_schedule (id, template_id, assignee_user_id, created_by_user_id,
                                               cadence, day_of_week, day_of_month, active,
                                               created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    assignee_user_id = excluded.assignee_user_id,
                    cadence = excluded.cadence,
                    day_of_week = excluded.day_of_week,
                    day_of_month = excluded.day_of_month,
                    active = excluded.active,
                    updated_at = excluded.updated_at
                """,
                schedule.id(),
                schedule.templateId(),
                schedule.assigneeId(),
                schedule.createdBy(),
                schedule.recurrence().cadence().name(),
                schedule.recurrence().dayOfWeek(),
                schedule.recurrence().dayOfMonth(),
                schedule.active(),
                java.sql.Timestamp.from(schedule.createdAt()),
                java.sql.Timestamp.from(schedule.updatedAt()));
    }

    @Override
    public Optional<TemplateSchedule> byId(UUID id) {
        return jdbc.query("select " + COLUMNS + " from template_schedule where id = ?", mapper(), id).stream()
                .findFirst();
    }

    @Override
    public List<TemplateSchedule> forTemplate(UUID templateId) {
        return jdbc.query(
                "select " + COLUMNS + " from template_schedule where template_id = ?"
                        + " order by active desc, created_at desc",
                mapper(),
                templateId);
    }

    @Override
    public List<TemplateSchedule> active() {
        return jdbc.query("select " + COLUMNS + " from template_schedule where active order by created_at", mapper());
    }

    @Override
    public boolean hasRaised(UUID scheduleId, LocalDate occurrence) {
        Integer found = jdbc.queryForObject(
                "select count(*) from template_schedule_run where schedule_id = ? and occurrence_date = ?",
                Integer.class,
                scheduleId,
                java.sql.Date.valueOf(occurrence));
        return found != null && found > 0;
    }

    @Override
    public void recordRun(UUID scheduleId, LocalDate occurrence, UUID taskId, Instant now) {
        jdbc.update(
                "insert into template_schedule_run (schedule_id, occurrence_date, task_id, created_at)"
                        + " values (?, ?, ?, ?)",
                scheduleId,
                java.sql.Date.valueOf(occurrence),
                taskId,
                java.sql.Timestamp.from(now));
    }

    @Override
    public History historyOf(UUID scheduleId) {
        return jdbc.queryForObject(
                "select count(*) as raised, max(occurrence_date) as last_on"
                        + " from template_schedule_run where schedule_id = ?",
                (row, index) -> new History(
                        row.getInt("raised"),
                        row.getDate("last_on") == null
                                ? null
                                : row.getDate("last_on").toLocalDate()),
                scheduleId);
    }

    private RowMapper<TemplateSchedule> mapper() {
        return (ResultSet row, int index) -> new TemplateSchedule(
                row.getObject("id", UUID.class),
                row.getObject("template_id", UUID.class),
                row.getObject("assignee_user_id", UUID.class),
                row.getObject("created_by_user_id", UUID.class),
                new Recurrence(
                        ScheduleCadence.valueOf(row.getString("cadence")),
                        nullableInt(row, "day_of_week"),
                        nullableInt(row, "day_of_month")),
                row.getBoolean("active"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private static Integer nullableInt(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }
}
