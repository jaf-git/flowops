package com.flowops.auth.domain.model;

import com.flowops.auth.domain.exception.DisplayNameRequiredException;

public record DisplayName(String value) {
    public static final int MAXIMUM_LENGTH = 120;

    public DisplayName {
        if (value == null) {
            throw new DisplayNameRequiredException();
        }
        value = value.trim();
        if (value.isEmpty() || value.length() > MAXIMUM_LENGTH) {
            throw new DisplayNameRequiredException();
        }
    }
}
