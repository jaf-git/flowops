package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.exception.InvalidEmailAddressException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private static final int MAX_LENGTH = 320;

    public EmailAddress {
        Objects.requireNonNull(value, "an address is required");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new InvalidEmailAddressException("an address is required");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidEmailAddressException("an address may not exceed " + MAX_LENGTH + " characters");
        }
        if (!SHAPE.matcher(value).matches()) {
            throw new InvalidEmailAddressException("that is not a valid email address");
        }
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }
}
