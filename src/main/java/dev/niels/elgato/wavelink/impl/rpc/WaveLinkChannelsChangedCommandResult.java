package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;

public record WaveLinkChannelsChangedCommandResult(List<WaveLinkChannel> channels) {
}
