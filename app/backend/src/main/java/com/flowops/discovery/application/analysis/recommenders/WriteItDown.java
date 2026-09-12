package com.flowops.discovery.application.analysis.recommenders;

import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.Recommendation;
import com.flowops.discovery.domain.analysis.RecommendationKind;
import com.flowops.discovery.domain.analysis.Recommender;
import com.flowops.discovery.domain.analysis.SubjectKind;
import com.flowops.shared.text.WorkTypeWords;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WriteItDown implements Recommender {
    @Override
    public String name() {
        return "write-it-down";
    }

    @Override
    public List<Recommendation> recommend(List<Finding> found) {
        Set<String> undermined = found.stream()
                .filter(finding -> "shape-rests-on-fragments".equals(finding.detector()))
                .map(Finding::subjectKey)
                .collect(Collectors.toSet());

        List<Recommendation> proposals = new ArrayList<>();

        for (Finding finding : found) {
            if (finding.subjectKind() != SubjectKind.SHAPE || !"recurring-shape".equals(finding.detector())) {
                continue;
            }

            boolean shaky = undermined.contains(finding.subjectKey());

            String headline = "Write down %s as a process".formatted(WorkTypeWords.shape(finding.subjectKey()));

            String detail = shaky
                    ? ("It ran the same way in %d jobs, but some of its steps are work types whose brackets "
                                    + "never join. Fix those addresses and run the analysis again — writing it down "
                                    + "now would record a sequence the business may not actually follow.")
                            .formatted(finding.sampleSize())
                    : ("It ran the same way in %d jobs. The steps come from work that actually happened, so "
                                    + "the draft is what people did rather than what anybody remembers.")
                            .formatted(finding.sampleSize());

            proposals.add(new Recommendation(
                    RecommendationKind.WRITE_IT_DOWN,
                    headline,
                    detail,
                    finding,
                    shaky
                            ? Recommendation.Confidence.UNDERMINED
                            : finding.sampleSize() >= 5
                                    ? Recommendation.Confidence.STRONG
                                    : Recommendation.Confidence.WORTH_LOOKING));
        }

        return proposals;
    }
}
