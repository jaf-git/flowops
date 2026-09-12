package com.flowops.workspace.application.shared.port;

public interface EraseInvitationTracesPort {
    void replaceAddress(String realAddress, String opaqueAddress);
}
