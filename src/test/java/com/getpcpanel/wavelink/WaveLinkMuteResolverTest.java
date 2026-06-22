package com.getpcpanel.wavelink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.mutecolor.MuteStateResolver;
import com.getpcpanel.mutecolor.WaveLinkMuteResolver;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute;
import com.getpcpanel.wavelink.command.WaveLinkCommandTarget;

import dev.niels.wavelink.impl.model.WaveLinkChannel;

/**
 * Tests the real {@link WaveLinkMuteResolver} against a Wave Link service that reports a given channel
 * mute — this is the exact piece that reads a muted "Music" channel for a slider bound to its volume.
 * Lives in the {@code wavelink} package so it can construct a {@link WaveLinkService} test double via
 * its package-private no-arg constructor.
 */
class WaveLinkMuteResolverTest {
    private static Commands channelVolume(String channelId) {
        Command cmd = new CommandWaveLinkChangeLevel(WaveLinkCommandTarget.Channel, channelId, null, null);
        return new Commands(List.of(cmd), CommandsType.allAtOnce);
    }

    private static Commands channelMuteButton(String channelId) {
        Command cmd = new CommandWaveLinkChangeMute(WaveLinkCommandTarget.Channel, channelId, null, MuteType.toggle);
        return new Commands(List.of(cmd), CommandsType.allAtOnce);
    }

    /** A WaveLinkService whose model knows exactly one channel with the given mute state. */
    private static WaveLinkService waveLinkWithChannel(String channelId, Boolean muted) {
        return new WaveLinkService() {
            @Override
            public WaveLinkChannel getChannelFromId(String id) {
                var isMuted = channelId.equals(id) ? muted : null;
                return new WaveLinkChannel(id, "Music", null, null, null, isMuted, null, null, null);
            }
        };
    }

    @Test
    void mutedChannelResolvesTrue() {
        var resolver = new WaveLinkMuteResolver(waveLinkWithChannel("music", true));
        assertEquals(Optional.of(true), resolver.resolve(channelVolume("music"), MuteStateResolver.FOLLOW));
    }

    @Test
    void mutedChannelViaMuteButtonResolvesTrue() {
        // A control whose turn command is the Wave Link mute *button* (not the level dial) must also
        // drive the mute-override colour — both carry the same CommandWaveLinkChange target.
        var resolver = new WaveLinkMuteResolver(waveLinkWithChannel("music", true));
        assertEquals(Optional.of(true), resolver.resolve(channelMuteButton("music"), MuteStateResolver.FOLLOW));
    }

    @Test
    void unmutedChannelResolvesFalse() {
        var resolver = new WaveLinkMuteResolver(waveLinkWithChannel("music", false));
        assertEquals(Optional.of(false), resolver.resolve(channelVolume("music"), MuteStateResolver.FOLLOW));
    }

    @Test
    void unknownChannelResolvesEmpty() {
        var resolver = new WaveLinkMuteResolver(waveLinkWithChannel("music", true));
        assertEquals(Optional.empty(), resolver.resolve(channelVolume("other"), MuteStateResolver.FOLLOW));
    }

    @Test
    void nonFollowTargetIsIgnored() {
        var resolver = new WaveLinkMuteResolver(waveLinkWithChannel("music", true));
        assertEquals(Optional.empty(), resolver.resolve(channelVolume("music"), "Some Device Name"));
    }
}
