package com.getpcpanel.integration.wavelink.command;

import javax.annotation.Nullable;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract sealed class CommandWaveLinkChange extends CommandWaveLink permits CommandWaveLinkChangeLevel, CommandWaveLinkChangeMute {
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

    /**
     * The live display name of the controlled target (channel/input/mix/output), or null when it can't
     * be resolved (Wave Link not connected, or unknown id). Used to build a meaningful label that matches
     * the UI's chip ("Browsers — Wave Link") instead of the generic "Set Channel".
     */
    @Nullable
    protected String targetName() {
        try {
            return getWaveLinkService().nameForId(id1);
        } catch (RuntimeException e) {
            return null; // no CDI / service available (e.g. outside the running app) → fall back
        }
    }
}
