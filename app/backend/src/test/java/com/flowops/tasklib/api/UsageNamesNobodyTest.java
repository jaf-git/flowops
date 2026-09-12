package com.flowops.tasklib.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.task.application.published.TemplateUsageUseCase;
import com.flowops.tasklib.api.dto.TemplateUsageResponse;
import com.flowops.tasklib.application.port.TemplatePerformancePort;
import com.flowops.tasklib.domain.TemplatePerformance;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class UsageNamesNobodyTest {
    private static final List<String> NAMES_A_PERSON =
            List.of("person", "user", "assignee", "reviewer", "author", "actor", "member", "who");

    @Test
    void noTypeOnTheUsageRouteCarriesAPerson() {
        List<String> offending = new ArrayList<>();
        for (Class<?> type : List.of(
                TemplateUsageResponse.class,
                TemplateUsageResponse.LiveWorkResponse.class,
                TemplateUsageUseCase.Usage.class,
                TemplateUsageUseCase.LiveCounts.class,
                TemplateUsageUseCase.ActiveTime.class,
                TemplateUsageUseCase.FirstTryApproval.class,
                TemplatePerformancePort.LiveWork.class,
                TemplatePerformance.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                if (NAMES_A_PERSON.stream().anyMatch(name::contains)) {
                    offending.add(type.getSimpleName() + "." + component.getName());
                }
            }
        }

        assertThat(offending)
                .as("the usage route must carry no person; the refusal is the absence of the field")
                .isEmpty();
    }

    @Test
    void thePublishedUsageInterfaceTakesAndReturnsNoPerson() {
        for (var method : TemplateUsageUseCase.class.getDeclaredMethods()) {
            for (var parameter : method.getParameters()) {
                String name = parameter.getName().toLowerCase(Locale.ROOT);
                assertThat(NAMES_A_PERSON.stream().anyMatch(name::contains))
                        .as("%s takes %s", method.getName(), parameter.getName())
                        .isFalse();
            }
        }
    }
}
