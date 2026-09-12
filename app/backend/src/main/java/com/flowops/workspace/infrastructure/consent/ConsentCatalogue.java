package com.flowops.workspace.infrastructure.consent;

import com.flowops.workspace.application.shared.port.ConsentCataloguePort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ConsentCatalogue implements ConsentCataloguePort {
    private static final List<String> LANGUAGES = List.of("en");

    private static final int VERSION_LENGTH = 12;

    private final Map<String, ConsentText> byLanguage;

    public ConsentCatalogue(@Value("${flowops.consent.path:consent/consent.%s.md}") String pathPattern) {
        Map<String, ConsentText> read = new LinkedHashMap<>();
        for (String language : LANGUAGES) {
            String text = readOrRefuseToStart(pathPattern.formatted(language));
            read.put(language, new ConsentText(language, versionOf(text), text));
        }
        this.byLanguage = Map.copyOf(read);
    }

    @Override
    public ConsentText inLanguage(String language) {
        if (language == null) {
            return byLanguage.get(LANGUAGES.getFirst());
        }
        return byLanguage.getOrDefault(language.toLowerCase(Locale.ROOT), byLanguage.get(LANGUAGES.getFirst()));
    }

    private static String readOrRefuseToStart(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "the consent text at " + path + " could not be read, so nobody could be shown what they"
                            + " are agreeing to",
                    e);
        }
    }

    private static String versionOf(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, VERSION_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
