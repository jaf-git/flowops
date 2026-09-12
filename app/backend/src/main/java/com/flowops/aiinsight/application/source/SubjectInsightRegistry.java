package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.domain.SubjectType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SubjectInsightRegistry {
    private final Map<SubjectType, SubjectInsightReader> readers = new EnumMap<>(SubjectType.class);

    public SubjectInsightRegistry(List<SubjectInsightReader> readers) {
        readers.forEach(reader -> this.readers.put(reader.subject(), reader));

        List<SubjectType> unserved = Arrays.stream(SubjectType.values())
                .filter(subject -> !this.readers.containsKey(subject))
                .toList();
        if (!unserved.isEmpty()) {
            throw new IllegalStateException("every subject type needs a reader; these have none: " + unserved);
        }
    }

    public SubjectInsightReader.Computed read(SubjectType subjectType, UUID subjectId) {
        return readers.get(subjectType).read(subjectId);
    }
}
