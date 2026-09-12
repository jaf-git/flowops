package com.flowops.canvas.application.stream;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CanvasHeartbeat {
    private static final Logger LOG = LoggerFactory.getLogger(CanvasHeartbeat.class);

    private final CanvasStreamRegistry registry;

    public CanvasHeartbeat(CanvasStreamRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${flowops.canvas.heartbeat-interval-ms:15000}")
    public void beat() {
        List<CanvasSubscription> streams = List.copyOf(registry.openStreams());

        for (CanvasSubscription stream : streams) {
            try {
                stream.sink().keepAlive();
            } catch (Exception gone) {
                LOG.debug("a stream could not be kept alive and is dropped", gone);
                registry.remove(stream.id());
                stream.sink().close();
            }
        }
    }
}
