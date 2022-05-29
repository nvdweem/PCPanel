package com.getpcpanel.cpp;

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
