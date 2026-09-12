package com.flowops.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
public abstract class ApplicationTest {
    @DynamicPropertySource
    static void useTheContainerDatabase(DynamicPropertyRegistry registry) {
        DatabaseContainer.registerOn(registry);
    }
}
