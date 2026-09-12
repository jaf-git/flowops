package com.flowops.tasklib.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.task.application.published.TemplateUsageUseCase;
import com.flowops.tasklib.api.dto.TemplateTaskResponse;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TemplateTasksCarryNoAggregateTest {
    private static final List<String> AGGREGATES = List.of(
            "seconds",
            "minutes",
            "hours",
            "duration",
            "elapsed",
            "active",
            "average",
            "median",
            "quartile",
            "score",
            "rate",
            "percent",
            "count",
            "total",
            "passed",
            "reviewed");

    @Test
    void noRowTypeCarriesAnythingThatCouldBeAggregated() {
        List<String> offending = new ArrayList<>();
        for (Class<?> type : List.of(TemplateUsageUseCase.TaskRow.class, TemplateTaskResponse.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                if (AGGREGATES.stream().anyMatch(name::contains)) {
                    offending.add(type.getSimpleName() + "." + component.getName());
                }
            }
        }

        assertThat(offending)
                .as("a row names a person, so it must carry no figure; with both, these become a scoreboard")
                .isEmpty();
    }

    @Test
    void theRowQueryTakesNoPerson() {
        List<String> namesAPerson = List.of("person", "user", "assignee", "reviewer", "author", "actor", "member");

        for (var method : TemplateUsageUseCase.class.getDeclaredMethods()) {
            for (var parameter : method.getParameters()) {
                String name = parameter.getName().toLowerCase(Locale.ROOT);
                assertThat(namesAPerson.stream().anyMatch(name::contains))
                        .as("%s takes %s", method.getName(), parameter.getName())
                        .isFalse();
            }
        }
    }
}
