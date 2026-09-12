package com.flowops.aiexport.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DiscoverySeedRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DiscoverySeedRunner.class);

    private final SeedWorkBrackets brackets;
    private final SeedRepeatedWork repeated;

    public DiscoverySeedRunner(SeedWorkBrackets brackets, SeedRepeatedWork repeated) {
        this.brackets = brackets;
        this.repeated = repeated;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        attempt("bracket walkthrough", brackets::walk);
        attempt("repeated work", repeated::walk);
    }

    private void attempt(String what, java.util.function.Supplier<String> seeder) {
        try {
            LOG.info("{} seed: {}", what, seeder.get());
        } catch (RuntimeException failed) {
            LOG.warn("{} seed did not complete: {}", what, failed.getMessage(), failed);
        }
    }
}
