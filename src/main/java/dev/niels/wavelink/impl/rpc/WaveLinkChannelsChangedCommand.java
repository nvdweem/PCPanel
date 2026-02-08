package dev.niels.wavelink.impl.rpc;

import java.util.List;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelsChangedCommand.WaveLinkChannelsChangedCommandResult;

public class WaveLinkChannelsChangedCommand extends WaveLinkJsonRpcCommand<WaveLinkChannelsChangedCommandResult, Void> {
    public record WaveLinkChannelsChangedCommandResult(List<WaveLinkChannel> channels) {
    }
}
