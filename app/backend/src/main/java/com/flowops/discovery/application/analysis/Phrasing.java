package com.flowops.discovery.application.analysis;

import com.flowops.discovery.domain.analysis.Finding;

public interface Phrasing {
    enum By {
        TEMPLATE,

        MODEL
    }

    Phrased phrase(Finding finding);

    record Phrased(String sentence, By by) {}
}
