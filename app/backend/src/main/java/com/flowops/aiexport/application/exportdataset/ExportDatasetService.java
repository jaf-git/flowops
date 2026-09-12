package com.flowops.aiexport.application.exportdataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowops.aiexport.application.AnalyticalRecords;
import com.flowops.aiexport.application.PerExportPseudonymiser;
import com.flowops.aiexport.application.port.AnalyticalReadPort;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportDatasetService {
    private static final List<String> RECORD_TYPES = List.of("task", "instance", "step", "conversion");

    private final AnalyticalReadPort reads;
    private final Clock clock;

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public ExportDatasetService(AnalyticalReadPort reads, Clock clock) {
        this.reads = reads;
        this.clock = clock;
    }

    public static class NoHistoryException extends RuntimeException {
        public NoHistoryException() {
            super("There is no history to export yet.");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Path writeToTemporaryFile(AnalyticalReadPort.Window window) {
        AnalyticalReadPort.Sufficiency sufficiency = reads.sufficiency(window);
        if (sufficiency.tasksTotal() == 0) {
            throw new NoHistoryException();
        }

        Path file;
        try {
            file = Files.createTempFile("flowops-export-", ".zip");
        } catch (IOException cannotWrite) {
            throw new UncheckedIOException("the export needs somewhere to write", cannotWrite);
        }

        PerExportPseudonymiser as = new PerExportPseudonymiser();

        try (OutputStream out = Files.newOutputStream(file);
                ZipOutputStream archive = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            writeFile(archive, "header.jsonl", writer -> writer.accept(header(as, window, sufficiency)));
            writeFile(
                    archive,
                    "tasks.jsonl",
                    writer -> reads.eachTask(window, as, task -> writer.accept(node("task", task))));
            writeFile(
                    archive,
                    "instances.jsonl",
                    writer -> reads.eachInstance(window, as, instance -> writer.accept(node("instance", instance))));
            writeFile(
                    archive,
                    "steps.jsonl",
                    writer -> reads.eachStep(window, step -> writer.accept(node("step", step))));
            writeFile(
                    archive,
                    "conversions.jsonl",
                    writer -> reads.eachConversion(window, as, c -> writer.accept(node("conversion", c))));
        } catch (IOException | RuntimeException failure) {
            deleteQuietly(file);
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new UncheckedIOException("the export failed part-way and was not kept", (IOException) failure);
        }
        return file;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException stubborn) {
        }
    }

    private ObjectNode header(
            PerExportPseudonymiser as, AnalyticalReadPort.Window window, AnalyticalReadPort.Sufficiency figures) {
        Instant from = window.from();
        Instant to = window.to();
        ObjectNode header = json.createObjectNode();
        header.put("record_type", "header");
        header.put("schema_version", AnalyticalRecords.SCHEMA_VERSION);
        header.put("workspace_ref", as.ofWorkspace());
        header.put("generated_at", clock.instant().toString());

        ObjectNode range = header.putObject("range");
        range.put("from", from == null ? null : from.toString());
        range.put("to", to == null ? null : to.toString());

        header.putPOJO("record_types", RECORD_TYPES);

        if (window.isScoped()) {
            ObjectNode scope = header.putObject("scope");
            scope.put("subject_type", "process_template");
            scope.put("subject_id", window.subjectId().toString());
        }

        ObjectNode counts = header.putObject("counts");
        counts.put("tasks", figures.tasksTotal());
        counts.put("instances", figures.instancesCompleted());

        ObjectNode enough = header.putObject("sufficiency");
        enough.put("tasks_total", figures.tasksTotal());
        enough.put("tasks_closed", figures.tasksClosed());

        if (figures.templatesTotal() != null) {
            enough.put("templates_total", figures.templatesTotal());
        }
        if (figures.templatesWithFewerThanFiveUses() != null) {
            enough.put("templates_with_fewer_than_5_uses", figures.templatesWithFewerThanFiveUses());
        }
        enough.put("instances_completed", figures.instancesCompleted());
        enough.put("templates_with_no_completed_instance", figures.templatesWithNoCompletedInstance());
        enough.put("steps_attached_ratio", figures.stepsAttachedRatio());
        enough.put("median_task_lifespan_days", figures.medianTaskLifespanDays());
        enough.put("date_range_days", figures.dateRangeDays());

        return header;
    }

    private ObjectNode node(String recordType, Object record) {
        ObjectNode wrapped = json.valueToTree(record);
        ObjectNode out = json.createObjectNode();

        out.put("schema_version", AnalyticalRecords.SCHEMA_VERSION);
        out.put("record_type", recordType);
        out.setAll(wrapped);
        return out;
    }

    private void writeFile(ZipOutputStream archive, String name, java.util.function.Consumer<LineSink> body)
            throws IOException {
        archive.putNextEntry(new ZipEntry(name));
        List<IOException> failed = new ArrayList<>(1);
        body.accept(line -> {
            try {
                archive.write(json.writeValueAsBytes(line));
                archive.write('\n');
            } catch (JsonProcessingException malformed) {
                throw new IllegalStateException("a record could not be written as JSON", malformed);
            } catch (IOException broken) {
                failed.add(broken);
                throw new UncheckedIOException(broken);
            }
        });
        archive.closeEntry();
        if (!failed.isEmpty()) {
            throw failed.get(0);
        }
    }

    private interface LineSink {
        void accept(ObjectNode line);
    }
}
