package com.flowops.aiinsight.domain;

public record ClosureCoverage(int excluded, int qualifying) {
    public static ClosureCoverage materialIn(int closed, int qualifying, int thresholdPercent) {
        int excluded = qualifying - closed;
        if (excluded <= 0 || qualifying <= 0) {
            return null;
        }

        if (closed * 100 >= (long) thresholdPercent * qualifying) {
            return null;
        }
        return new ClosureCoverage(excluded, qualifying);
    }
}
