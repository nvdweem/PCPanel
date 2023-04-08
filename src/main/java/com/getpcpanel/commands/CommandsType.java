package com.getpcpanel.commands;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CommandsType {
    allAtOnce("All at once"),
    sequential("In sequence");

    private final String label;

    @Override
    public String toString() {
        return label;
    }
}
