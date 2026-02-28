package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkChannelsChangedCommand.WaveLinkChannelsChangedCommandResult;

public class WaveLinkChannelsChangedCommand extends WaveLinkJsonRpcCommand<WaveLinkChannelsChangedCommandResult, Void> {
    public record WaveLinkChannelsChangedCommandResult(List<WaveLinkChannel> channels) {
    }
}
