package com.getpcpanel.integration.volume.platform;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    roleConsole, roleMultimedia, roleCommunications;

    public static Role from(int ord) {
        return values()[ord];
    }
}
