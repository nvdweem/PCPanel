package com.getpcpanel.integration.sonar.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.integration.sonar.SonarChannel;
import com.getpcpanel.integration.sonar.SonarMixSelection;
import com.getpcpanel.integration.sonar.SonarService;
import com.getpcpanel.integration.testutil.FakeCdi;

/**
 * {@link CommandSonarVolume}: the readiness guard and the value pipeline — the dial's 0..1 position
 * (already curve/trim/invert-adjusted by {@link DialValue}) reaches {@link SonarService#setVolume}
 * unscaled. The service is a hand-written recording stub served through {@link FakeCdi}, the same
 * pattern {@code CommandOscSendTest} uses.
 */
@DisplayName("CommandSonarVolume: execute behaviour")
class CommandSonarVolumeTest {
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

    private static DialActionParameters dialAt(int raw) {
        return new DialActionParameters("device", false, new DialValue(null, Curve.LINEAR, raw));
    }

    @Test
    @DisplayName("does nothing when Sonar is not ready")
    void doesNothingWhenNotReady() {
        sonar.setReady(false);
        var command = new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null);

        command.execute(dialAt(255));

        assertTrue(sonar.volumeCalls.isEmpty());
    }

    @Test
    @DisplayName("passes the dial's 0..1 value through unscaled")
    void passesValueThroughUnscaled() {
        var command = new CommandSonarVolume(SonarChannel.Chat, SonarMixSelection.streaming, false, null);

        command.execute(dialAt(255));
        command.execute(dialAt(0));

        assertEquals(2, sonar.volumeCalls.size());
        assertEquals(new FakeSonarService.VolumeCall(SonarChannel.Chat, SonarMixSelection.streaming, 1.0), sonar.volumeCalls.get(0));
        assertEquals(new FakeSonarService.VolumeCall(SonarChannel.Chat, SonarMixSelection.streaming, 0.0), sonar.volumeCalls.get(1));
    }
}
