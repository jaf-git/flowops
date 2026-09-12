package com.flowops.discovery.infrastructure.analysis;

import com.flowops.discovery.application.analysis.Phrasing;
import com.flowops.discovery.domain.analysis.Finding;
import org.springframework.stereotype.Component;

@Component
public class TemplatePhrasing implements Phrasing {
    @Override
    public Phrased phrase(Finding finding) {
        String evidence = finding.sampleSize() == 1 ? "1 case" : finding.sampleSize() + " cases";

        String sentence = "%s. Measured across %s.".formatted(finding.headline(), evidence);

        return new Phrased(sentence, By.TEMPLATE);
    }
}
