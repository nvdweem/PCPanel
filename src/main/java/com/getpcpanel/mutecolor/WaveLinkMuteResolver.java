package com.getpcpanel.mutecolor;

import java.util.Optional;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.wavelink.WaveLinkService;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Mute state of a Wave Link volume control ({@link CommandWaveLinkChangeLevel}) — channel, mix or output. */
@ApplicationScoped
public class WaveLinkMuteResolver implements MuteStateResolver {
    @Inject
    WaveLinkService waveLink;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        var cmd = command.getCommand(CommandWaveLinkChangeLevel.class).orElse(null);
        if (cmd == null || !waveLink.isConnected() || cmd.getId1() == null) {
            return Optional.empty();
        }
        return switch (cmd.getCommandType()) {
            case Channel -> Optional.ofNullable(waveLink.getChannelFromId(cmd.getId1()).isMuted());
            case Mix -> {
                var channel = waveLink.getChannelFromId(cmd.getId1());
                var mix = channel.findMix(cmd.getId2()).orElseGet(() -> waveLink.getMixFromId(cmd.getId2()));
                yield Optional.ofNullable(mix.isMuted());
            }
            case Output -> {
                var output = waveLink.getOutputFromId(cmd.getId1());
                yield output.outputs() == null || output.outputs().isEmpty()
                        ? Optional.empty()
                        : Optional.ofNullable(output.outputs().get(0).isMuted());
            }
            case Input -> Optional.empty(); // Wave Link input mute is not exposed by the API
        };
    }
}
