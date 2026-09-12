package com.flowops.discovery.application.markmessage;

import com.flowops.discovery.application.closejob.JobAlreadyEndedException;
import com.flowops.discovery.application.shared.WorkCapture;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkMessageService implements MarkMessageUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final WorkCapture capture;
    private final Clock clock;

    public MarkMessageService(IdentifyCallerPort caller, WorkGraphPort graph, WorkCapture capture, Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.capture = capture;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Marked execute(MarkMessage command) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        JobId job = JobId.of(command.jobId());
        Job engagement = graph.findJob(job).orElseThrow(() -> new UnknownJobException(command.jobId()));

        if (engagement.isEnded()) {
            throw new JobAlreadyEndedException(job, engagement.status());
        }

        WorkCapture.Captured captured = capture.capture(
                command.messageId(), me, job, command.direction(), NodeKind.WORK, command.performerId());

        engagement.touched(clock.instant());
        graph.save(engagement);

        return new Marked(captured.node().id(), captured.threadedInto().map(Track::id), captured.conversationId());
    }
}
