package com.flowops.auth.domain.model;

public final class AuthPermissions {
    public static final String SESSION_VIEW_OWN = "SESSION_VIEW_OWN";
    public static final String SESSION_END_OWN = "SESSION_END_OWN";
    public static final String SESSION_VIEW_ANY = "SESSION_VIEW_ANY";
    public static final String SESSION_TERMINATE_ANY = "SESSION_TERMINATE_ANY";
    public static final String CREDENTIAL_REAUTH_OWN = "CREDENTIAL_REAUTH_OWN";
    public static final String CREDENTIAL_CHANGE_OWN = "CREDENTIAL_CHANGE_OWN";

    private AuthPermissions() {}
}
