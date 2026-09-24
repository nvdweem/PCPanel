package com.getpcpanel.integration.sonar.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.sonar.SonarChannel;
import com.getpcpanel.integration.sonar.SonarMixSelection;
import com.getpcpanel.integration.sonar.SonarService;
import com.getpcpanel.integration.testutil.FakeCdi;
import com.getpcpanel.integration.volume.platform.MuteType;

/**
 * {@link CommandSonarMute}: the readiness guard, and how each {@link MuteType} resolves against the
 * current state {@link SonarService#mutedOrNull} reports. {@code mute}/{@code unmute} are absolute —
 * they never need the current state — while {@code toggle} needs it to invert, and must not guess when
 * it is unknown (see the class javadoc on {@link CommandSonarMute} for why).
 */
@DisplayName("CommandSonarMute: execute behaviour")
class CommandSonarMuteTest {
    private FakeSonarService sonar;

    @BeforeEach
    void setUp() {
        sonar = new FakeSonarService();
        FakeCdi.register(SonarService.class, sonar);
    }

    @AfterEach
    void tearDown() {
        FakeCdi.clear();
    }

    @Test
    @DisplayName("does nothing when Sonar is not ready")
    void doesNothingWhenNotReady() {
        sonar.setReady(false);
        var command = new CommandSonarMute(SonarChannel.Game, SonarMixSelection.monitoring, MuteType.mute);

        command.execute();

        assertTrue(sonar.muteCalls.isEmpty());
    }

    @Test
    @DisplayName("mute always sends true, regardless of the current state")
    void muteSendsTrue() {
        sonar.setCurrentMuted(false);
        var command = new CommandSonarMute(SonarChannel.Game, SonarMixSelection.monitoring, MuteType.mute);

        command.execute();

        assertEquals(new FakeSonarService.MuteCall(SonarChannel.Game, SonarMixSelection.monitoring, true), sonar.muteCalls.get(0));
    }

    @Test
    @DisplayName("unmute always sends false, regardless of the current state")
    void unmuteSendsFalse() {
        sonar.setCurrentMuted(true);
        var command = new CommandSonarMute(SonarChannel.Chat, SonarMixSelection.streaming, MuteType.unmute);

        command.execute();

        assertEquals(new FakeSonarService.MuteCall(SonarChannel.Chat, SonarMixSelection.streaming, false), sonar.muteCalls.get(0));
    }

    @Test
    @DisplayName("toggle inverts a known current state")
    void toggleInvertsKnownState() {
        sonar.setCurrentMuted(true);
        var command = new CommandSonarMute(SonarChannel.Mic, SonarMixSelection.monitoring, MuteType.toggle);

        command.execute();

        assertEquals(new FakeSonarService.MuteCall(SonarChannel.Mic, SonarMixSelection.monitoring, false), sonar.muteCalls.get(0));
    }

    @Test
    @DisplayName("toggle with an unknown current state sends nothing rather than guessing")
    void toggleWithUnknownStateSendsNothing() {
        sonar.setCurrentMuted(null);
        var command = new CommandSonarMute(SonarChannel.Master, SonarMixSelection.streaming, MuteType.toggle);

        command.execute();

        assertTrue(sonar.muteCalls.isEmpty());
    }
}
