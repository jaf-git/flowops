package com.flowops.discovery.application.shared.port;

import java.util.List;
import java.util.Map;

public interface GovernedWorkTypePort {
    List<WorkTypeEntry> vocabulary();

    Map<String, String> aliases();

    Map<TypePair, Double> family(int version);

    record WorkTypeEntry(String code, String label, boolean active, int displayOrder) {}

    record TypePair(String a, String b) {
        public static TypePair of(String one, String other) {
            return one.compareTo(other) <= 0 ? new TypePair(one, other) : new TypePair(other, one);
        }
    }
}
