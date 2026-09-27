package com.getpcpanel.commands;

import java.util.List;

/** A command that runs other commands of its own, such as the bands of a stepped-switch dial. */
public interface NestedCommands {
    /** Every command set this command can run, whichever one a given input selects. */
    List<Commands> nestedCommands();
}
