package com.getpcpanel.elgato.wavelink.command;

import javax.annotation.Nullable;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract class CommandWaveLinkChange extends CommandWaveLink {
    private final WaveLinkCommandTarget commandType;
    @Nullable private final String id1;
    @Nullable private final String id2;

    protected CommandWaveLinkChange(
            WaveLinkCommandTarget commandType,
            @Nullable String id1,
            @Nullable String id2) {
        this.commandType = commandType;
        this.id1 = id1;
        this.id2 = id2;
    }
}
