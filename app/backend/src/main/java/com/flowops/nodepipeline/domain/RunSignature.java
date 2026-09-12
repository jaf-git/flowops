package com.flowops.nodepipeline.domain;

import com.flowops.nodepipeline.domain.match.Lexicons;
import com.flowops.nodepipeline.domain.match.MatchWeights;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public final class RunSignature {
    private RunSignature() {}

    public static String of(
            MatchWeights weights, Lexicons lexicons, String aiMode, String modelId, String promptVersion) {
        List<String> fields = new ArrayList<>();
        fields.add("w.text=" + weights.textWeight());
        fields.add("w.role=" + weights.roleWeight());
        fields.add("w.shape=" + weights.shapeWeight());
        fields.add("w.output=" + weights.outputWeight());
        fields.add("f.score=" + weights.scoreFloor());
        fields.add("f.confidence=" + weights.confidenceFloor());
        fields.add("f.separation=" + weights.separationMinimum());
        fields.add("f.textVeto=" + weights.textVeto());
        fields.add("f.textQuality=" + weights.textQualityFloor());
        fields.add("f.plausible=" + weights.plausibleFloor());
        fields.add("f.unique=" + weights.uniqueFloor());
        fields.add("f.keywordCorroboration=" + weights.keywordCorroboration());
        fields.add("finalists=" + weights.finalists());

        MatchWeights.Gates gates = weights.gates();
        fields.add("g.boundary=" + gates.boundary());
        fields.add("g.closure=" + gates.closure());
        fields.add("g.intent=" + gates.intent());
        fields.add("g.direction=" + gates.direction());
        fields.add("g.postClose=" + gates.postClose());
        fields.add("g.shape=" + gates.shapeFeature());
        fields.add("g.output=" + gates.outputMap());
        fields.add("g.keywords=" + gates.keywords());
        fields.add("g.typeContradiction=" + gates.typeContradiction());
        fields.add("g.markerAddress=" + gates.markerAddress());

        lexicons.roleKinship().entrySet().stream()
                .map(e -> "kin." + e.getKey().a() + "~" + e.getKey().b() + "=" + e.getValue())
                .sorted()
                .forEach(fields::add);
        lexicons.typeWords().entrySet().stream()
                .map(e -> "type." + e.getKey() + "="
                        + e.getValue().stream().sorted().reduce("", (a, b) -> a + "," + b))
                .sorted()
                .forEach(fields::add);

        fields.add("ai.mode=" + aiMode);
        fields.add("ai.model=" + (modelId == null ? "-" : modelId));
        fields.add("ai.prompt=" + (promptVersion == null ? "-" : promptVersion));

        return sha256(String.join("\n", fields));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required to sign a pipeline run", impossible);
        }
    }
}
