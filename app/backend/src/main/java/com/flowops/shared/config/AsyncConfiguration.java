package com.flowops.shared.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@ConfigurationPropertiesScan("com.flowops")
public class AsyncConfiguration {}
