package com.flowops.analyser.domain;

public interface Analyser {
    String id();

    Category category();

    AnalyserStage stage();

    Report analyse(Snapshot snapshot);
}
