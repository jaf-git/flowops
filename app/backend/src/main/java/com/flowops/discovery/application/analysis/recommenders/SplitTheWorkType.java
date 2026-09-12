package com.flowops.discovery.application.analysis.recommenders;

import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.Recommendation;
import com.flowops.discovery.domain.analysis.RecommendationKind;
import com.flowops.discovery.domain.analysis.Recommender;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SplitTheWorkType implements Recommender {
    @Override
    public String name() {
        return "split-the-work-type";
    }

    @Override
    public List<Recommendation> recommend(List<Finding> found) {
        List<Recommendation> proposals = new ArrayList<>();

        for (Finding finding : found) {
            if (!"fragmentation".equals(finding.detector())) {
                continue;
            }

            String headline = "Look at how %s work is addressed".formatted(finding.subjectKey());

            String detail = ("%d of its brackets opened and closed without ever joining anything. That usually "
                            + "means one work type is covering two different streams, or that the same job is "
                            + "being marked more than once. Splitting the role, or the work type, makes the "
                            + "rest of the analysis about %s trustworthy.")
                    .formatted(finding.sampleSize(), finding.subjectKey());

            proposals.add(new Recommendation(
                    RecommendationKind.SPLIT_WORK_TYPE,
                    headline,
                    detail,
                    finding,
                    finding.sampleSize() >= 8
                            ? Recommendation.Confidence.STRONG
                            : Recommendation.Confidence.WORTH_LOOKING));
        }

        return proposals;
    }
}
