package com.flowops.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.shared.config.SchedulingConfiguration;
import com.flowops.support.ApplicationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

@Tag("architecture")
class SchedulingIsOffUnderTestTest extends ApplicationTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void theSchedulerIsNotWiredIntoATestContext() {
        assertThat(context.getBeanNamesForType(SchedulingConfiguration.class))
                .describedAs("flowops.scheduling.enabled must be false for tests: a scheduled sweep and a "
                        + "truncating cleanup deadlock, and the failure is a race rather than a "
                        + "repeatable one")
                .isEmpty();
    }
}
