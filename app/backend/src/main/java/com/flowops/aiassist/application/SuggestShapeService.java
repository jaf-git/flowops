package com.flowops.aiassist.application;

import com.flowops.aiassist.application.port.LanguageModelPort;
import com.flowops.aiassist.application.port.SubjectEvidencePort;
import com.flowops.aiassist.domain.EvidencePacket;
import com.flowops.aiassist.domain.GroundedSuggestion;
import com.flowops.aiassist.domain.GroundingValidator;
import com.flowops.aiassist.domain.ShapeOpinion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SuggestShapeService implements SuggestShapeUseCase {
    private static final Duration REMEMBERED_FOR = Duration.ofMinutes(10);

    private final SubjectEvidencePort evidence;
    private final LanguageModelPort model;
    private final Clock clock;

    private final Map<UUID, Remembered> remembered = new ConcurrentHashMap<>();

    public SuggestShapeService(SubjectEvidencePort evidence, LanguageModelPort model, Clock clock) {
        this.evidence = evidence;
        this.model = model;
        this.clock = clock;
    }

    @Override
    public boolean isAvailable() {
        return model.isAvailable();
    }

    @Override
    public Optional<GroundedSuggestion> shapeOf(UUID subjectId) {
        Instant now = clock.instant();

        Remembered previous = remembered.get(subjectId);
        if (previous != null && previous.stillGoodAt(now)) {
            return previous.answer();
        }

        if (!model.isAvailable()) {
            return Optional.empty();
        }

        Optional<EvidencePacket> packet = evidence.gather(subjectId);
        if (packet.isEmpty() || packet.get().isEmpty()) {
            return remember(subjectId, now, Optional.empty());
        }

        Optional<ShapeOpinion> opinion = model.readShapeOf(packet.get());
        if (opinion.isEmpty()) {
            return Optional.empty();
        }

        return remember(subjectId, now, GroundingValidator.check(opinion.get(), packet.get()));
    }

    private Optional<GroundedSuggestion> remember(UUID subjectId, Instant now, Optional<GroundedSuggestion> answer) {
        remembered.put(subjectId, new Remembered(answer, now.plus(REMEMBERED_FOR)));
        return answer;
    }

    private record Remembered(Optional<GroundedSuggestion> answer, Instant until) {
        boolean stillGoodAt(Instant now) {
            return now.isBefore(until);
        }
    }
}
