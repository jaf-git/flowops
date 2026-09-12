package com.flowops.discovery.domain.analysis;

import java.util.List;

public interface Recommender {
    String name();

    List<Recommendation> recommend(List<Finding> found);
}
