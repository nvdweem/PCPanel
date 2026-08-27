package com.getpcpanel.integration.voicemeeter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.voicemeeter.Voicemeeter.ButtonControlMode;
import com.getpcpanel.integration.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.integration.voicemeeter.Voicemeeter.ControlType;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterBasic;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.integration.volume.mutecolor.MuteOverridesDirtyEvent;
import com.getpcpanel.integration.volume.mutecolor.MuteStateResolver;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/**
 * A VoiceMeeter strip can be muted from a button as well as from a dial, so the muted colour has to
 * follow either (issue #155).
 */
class VoiceMeeterMuteResolverTest {
    private static Commands commands(Command... cmds) {
        return new Commands(List.of(cmds), CommandsType.allAtOnce);
    }

    private static VoiceMeeterMuteResolver resolverWithMuted(ControlType ct, int idx, boolean muted) {
        var resolver = new VoiceMeeterMuteResolver();
        resolver.dirty = new NoopEvent();
        resolver.onMute(new VoiceMeeterMuteEvent(ct, idx, ButtonType.MUTE, muted));
        return resolver;
    }

    @Test
    void basicMuteButtonFollowsItsOwnStrip() {
        var resolver = resolverWithMuted(ControlType.STRIP, 2, true);

        var muted = resolver.resolve(commands(new CommandVoiceMeeterBasicButton(ControlType.STRIP, 2, ButtonType.MUTE)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent(), "a VoiceMeeter mute button must resolve its own strip's mute state");
        assertEquals(true, muted.get());
    }

    @Test
    void advancedButtonFollowsTheStripItAddresses() {
        var resolver = resolverWithMuted(ControlType.BUS, 1, true);

        var muted = resolver.resolve(commands(new CommandVoiceMeeterAdvancedButton("Bus[1].Mute", ButtonControlMode.TOGGLE, null)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent(), "an advanced button's fullParam names the bus to follow");
        assertEquals(true, muted.get());
    }

    @Test
    void theDialWinsOverAButtonOnTheSameControl() {
        var resolver = resolverWithMuted(ControlType.STRIP, 0, true);
        resolver.onMute(new VoiceMeeterMuteEvent(ControlType.STRIP, 3, ButtonType.MUTE, false));

        var muted = resolver.resolve(commands(
                new CommandVoiceMeeterBasic(ControlType.STRIP, 0, null, null),
                new CommandVoiceMeeterBasicButton(ControlType.STRIP, 3, ButtonType.MUTE)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isPresent());
        assertEquals(true, muted.get(), "the dial's strip (muted) drives the colour, not the button's");
    }

    @Test
    void anUnknownStripStaysUnresolved() {
        var resolver = resolverWithMuted(ControlType.STRIP, 2, true);

        var muted = resolver.resolve(commands(new CommandVoiceMeeterBasicButton(ControlType.STRIP, 7, ButtonType.MUTE)), MuteStateResolver.FOLLOW);

        assertTrue(muted.isEmpty(), "a strip VoiceMeeter has never reported on is unknown, not unmuted");
    }

    /** Minimal {@link Event}; the resolver only ever fires on it, and nothing here observes. */
    private static final class NoopEvent implements Event<MuteOverridesDirtyEvent> {
        private final List<Object> fired = new CopyOnWriteArrayList<>();

        @Override
        public void fire(MuteOverridesDirtyEvent event) {
            fired.add(event);
        }

        @Override
        public <U extends MuteOverridesDirtyEvent> CompletionStage<U> fireAsync(U event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends MuteOverridesDirtyEvent> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Event<MuteOverridesDirtyEvent> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends MuteOverridesDirtyEvent> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends MuteOverridesDirtyEvent> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }
    }
}
