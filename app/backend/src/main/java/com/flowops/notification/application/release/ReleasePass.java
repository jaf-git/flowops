package com.flowops.notification.application.release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReleasePass {
    private static final Logger LOG = LoggerFactory.getLogger(ReleasePass.class);

    private final ReleaseUseCase release;

    public ReleasePass(ReleaseUseCase release) {
        this.release = release;
    }

    @Scheduled(fixedDelayString = "${flowops.notification.release-pass-ms:60000}")
    public void releaseWhatIsDue() {
        try {
            int delivered = release.release();
            if (delivered > 0) {
                LOG.debug("release pass delivered {} held notifications", delivered);
            }
        } catch (RuntimeException failure) {
            LOG.warn("release pass failed; the next beat retries everything still due", failure);
        }
    }
}
