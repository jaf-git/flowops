package com.flowops.auth.domain.model;

import com.flowops.auth.domain.exception.InvalidEmailAddressException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAXIMUM_LENGTH = 320;

    public EmailAddress {
        Objects.requireNonNull(value, "an email address is required");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAXIMUM_LENGTH || !SHAPE.matcher(value).matches()) {
            throw new InvalidEmailAddressException();
        }
    }

    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }
}
