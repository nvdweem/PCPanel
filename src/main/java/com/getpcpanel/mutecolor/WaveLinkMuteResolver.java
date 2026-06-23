package com.getpcpanel.mutecolor;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.wavelink.WaveLinkService;
import com.getpcpanel.wavelink.command.CommandWaveLinkChange;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mute state of a Wave Link control — channel, mix or output — followed by either a level dial
 * ({@code CommandWaveLinkChangeLevel}) or a mute button ({@code CommandWaveLinkChangeMute}). Both
 * carry the same {@link CommandWaveLinkChange} target (command type + id1/id2), so either drives the
 * mute-override colour.
 */
@ApplicationScoped
public class WaveLinkMuteResolver implements MuteStateResolver {
    private final WaveLinkService waveLink;

    @Inject
    public WaveLinkMuteResolver(WaveLinkService waveLink) {
        this.waveLink = waveLink;
    }

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        var cmd = command.getCommand(CommandWaveLinkChange.class).orElse(null);
        if (cmd == null || cmd.getId1() == null) {
            return Optional.empty();
        }
        // No isConnected() gate: when Wave Link is down the lookups return a blank entry whose mute is
        // null, which maps to empty (unknown) anyway — and gating on it risks suppressing a real mute
        // if the flag lags the channel push.
        return switch (cmd.getCommandType()) {
            case Channel -> Optional.ofNullable(waveLink.getChannelFromId(cmd.getId1()).isMuted());
            case Mix -> {
                if (StringUtils.isBlank(cmd.getId2())) {
                    // getMixFromId(null) would hit ConcurrentHashMap.getOrDefault(null, …) and NPE; a
                    // partially-configured Mix command legitimately has no mix id yet (the command's own
                    // execute() guards the same way), so treat it as "mute unknown".
                    yield Optional.empty();
                }
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
