package com.flowops.notification.application.shared;

import com.flowops.notification.application.shared.port.SubjectStatePort;
import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.SubjectKind;
import com.flowops.shared.notice.SubjectRef;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SubjectStateRegistry {
    private final Map<SubjectKind, SubjectStatePort> byKind = new EnumMap<>(SubjectKind.class);

    public SubjectStateRegistry(List<SubjectStatePort> ports) {
        for (SubjectStatePort port : ports) {
            SubjectStatePort clash = byKind.put(port.answersFor(), port);
            if (clash != null) {
                throw new IllegalStateException("two ports answer for " + port.answersFor()
                        + "; the re-check would depend on bean ordering, which is not a rule");
            }
        }
        for (SubjectKind kind : SubjectKind.values()) {
            if (!byKind.containsKey(kind)) {
                throw new IllegalStateException("no SubjectStatePort answers for " + kind
                        + "; every notice about one would be cancelled at release without anybody being told");
            }
        }
    }

    public boolean stillRelevant(CancelCondition condition, SubjectRef subject) {
        if (condition == CancelCondition.NEVER) {
            return true;
        }
        return byKind.get(subject.kind()).stillRelevant(condition, subject.id());
    }

    public boolean stillExists(SubjectRef subject) {
        return byKind.get(subject.kind()).stillExists(subject.id());
    }
}
