package com.flowops.task.domain.model;

import com.flowops.task.domain.exception.LinkSchemeNotAllowedException;
import com.flowops.task.domain.exception.LinkUrlRequiredException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public final class LinkUrl {
    private static final Set<String> ALLOWED = Set.of("http", "https");

    private final String value;

    private LinkUrl(String value) {
        this.value = value;
    }

    public static LinkUrl of(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new LinkUrlRequiredException();
        }

        URI parsed;
        try {
            parsed = new URI(trimmed);
        } catch (URISyntaxException malformed) {
            throw new LinkSchemeNotAllowedException(trimmed);
        }

        String scheme = parsed.getScheme();
        if (scheme == null || !ALLOWED.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new LinkSchemeNotAllowedException(trimmed);
        }
        return new LinkUrl(trimmed);
    }

    public static LinkUrl rebuild(String stored) {
        return new LinkUrl(stored);
    }

    public String value() {
        return value;
    }

    public String host() {
        try {
            String host = new URI(value).getHost();
            return host == null ? "" : host;
        } catch (URISyntaxException impossible) {
            return "";
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LinkUrl url && value.equals(url.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
