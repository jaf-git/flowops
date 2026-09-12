package com.flowops.chat.infrastructure.event;

import com.flowops.chat.application.managerooms.ManageRoomsService;
import com.flowops.chat.application.shared.port.ChatDirectoryPort;
import com.flowops.shared.event.FunctionalRoleAssigned;
import com.flowops.shared.event.FunctionalRoleCreated;
import com.flowops.shared.event.FunctionalRoleRenamed;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RoleRoomListener {
    private final ManageRoomsService rooms;
    private final ChatDirectoryPort directory;

    public RoleRoomListener(ManageRoomsService rooms, ChatDirectoryPort directory) {
        this.rooms = rooms;
        this.directory = directory;
    }

    @EventListener
    public void onRoleCreated(FunctionalRoleCreated created) {
        rooms.channelForRole(directory.currentWorkspaceId(), created.roleId(), created.name());
    }

    @EventListener
    public void onRoleAssigned(FunctionalRoleAssigned assigned) {
        rooms.roleMembershipChanged(
                directory.currentWorkspaceId(), assigned.personId(), assigned.roleId(), assigned.previousRoleId());
    }

    @EventListener
    public void onRoleRenamed(FunctionalRoleRenamed renamed) {
        rooms.renameChannelForRole(directory.currentWorkspaceId(), renamed.roleId(), renamed.name());
    }
}
