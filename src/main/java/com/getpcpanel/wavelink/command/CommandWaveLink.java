package com.getpcpanel.wavelink.command;

import com.getpcpanel.MainFX;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.wavelink.WaveLinkService;

import io.reactivex.annotations.Nullable;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract class CommandWaveLink extends Command {
    private final WaveLinkCommandTarget commandType;
    @Nullable private final String id1;
    @Nullable private final String id2;

    protected CommandWaveLink(
            WaveLinkCommandTarget commandType,
            @Nullable String id1,
            @Nullable String id2) {
        this.commandType = commandType;
        this.id1 = id1;
        this.id2 = id2;
    }

    protected WaveLinkService getWaveLinkService() {
        return MainFX.getBean(WaveLinkService.class);
    }
}
